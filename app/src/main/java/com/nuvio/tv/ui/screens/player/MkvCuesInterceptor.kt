package com.nuvio.tv.ui.screens.player

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Makes "two-level SeekHead" MKVs seekable in ExoPlayer.
 *
 * Many remuxed MKVs keep Cues at the end of the file and only reference them from a
 * second SeekHead; the first SeekHead just points at that second one. Media3's
 * MatroskaExtractor doesn't follow nested SeekHeads, finds no Cues and marks the
 * stream unseekable. Here we look up the real Cues position once per URL and rewrite
 * the first SeekHead entry (same byte length) to point straight at the Cues.
 */
class MkvCuesInterceptor : Interceptor {
  private data class Patch(val offset: Long, val bytes: ByteArray)

  private val patches = ConcurrentHashMap<String, List<Patch>>()

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val response = chain.proceed(request)
    if (request.method != "GET" || !response.isSuccessful) return response
    val start = rangeStart(response)
    if (start > 64 * 1024) return response
    val url = request.url.toString()

    val list = patches[url] ?: run {
      val head = try { response.peekBody(4).bytes() } catch (_: Exception) { return response }
      val isMkv = start == 0L && head.size == 4 &&
        head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()
      if (!isMkv) {
        if (start == 0L) patches[url] = emptyList()
        return response
      }
      val computed = try { computePatches(chain, request) } catch (_: Exception) { emptyList() }
      patches[url] = computed
      computed
    }
    if (list.isEmpty()) return response
    val body = response.body ?: return response
    val patched = PatchingSource(body.source(), start, list).buffer()
    return response.newBuilder().body(patched.asResponseBody(body.contentType(), body.contentLength())).build()
  }

  private fun rangeStart(response: Response): Long {
    if (response.code != 206) return 0
    val cr = response.header("Content-Range") ?: return 0
    return Regex("""bytes\s+(\d+)-""").find(cr)?.groupValues?.get(1)?.toLongOrNull() ?: 0
  }

  private fun fetch(chain: Interceptor.Chain, request: Request, from: Long, len: Int): ByteArray {
    val r = request.newBuilder().header("Range", "bytes=$from-${from + len - 1}").build()
    chain.proceed(r).use { return it.body?.bytes() ?: ByteArray(0) }
  }

  // ── EBML helpers ───────────────────────────────────────────────────────────
  private class Reader(val b: ByteArray, var p: Int = 0)

  private fun readId(r: Reader): Long {
    val first = r.b[r.p].toInt() and 0xFF
    val len = Integer.numberOfLeadingZeros(first) - 23
    var v = 0L
    for (i in 0 until len) v = (v shl 8) or (r.b[r.p + i].toLong() and 0xFF)
    r.p += len
    return v
  }

  private fun readSize(r: Reader): Long {
    val first = r.b[r.p].toInt() and 0xFF
    val len = Integer.numberOfLeadingZeros(first) - 23
    var v = (first and (0xFF shr len)).toLong()
    for (i in 1 until len) v = (v shl 8) or (r.b[r.p + i].toLong() and 0xFF)
    r.p += len
    return v
  }

  private data class SeekEntry(val id: Long, val idOffset: Int, val idLen: Int, val pos: Long, val posOffset: Int, val posLen: Int)

  /** Parses the SeekHead element starting at [at]; returns its Seek entries. */
  private fun seekEntries(b: ByteArray, at: Int): List<SeekEntry> {
    val r = Reader(b, at)
    if (readId(r) != 0x114D9B74L) return emptyList()
    val end = r.p + readSize(r).toInt()
    val out = mutableListOf<SeekEntry>()
    while (r.p < end && r.p < b.size - 8) {
      val id = readId(r)
      val size = readSize(r).toInt()
      if (id != 0x4DBBL) { r.p += size; continue }
      val seekEnd = r.p + size
      var sid = -1L; var sidOff = 0; var sidLen = 0; var pos = -1L; var posOff = 0; var posLen = 0
      while (r.p < seekEnd) {
        val cid = readId(r)
        val clen = readSize(r).toInt()
        when (cid) {
          0x53ABL -> { sidOff = r.p; sidLen = clen; sid = 0; for (i in 0 until clen) sid = (sid shl 8) or (b[r.p + i].toLong() and 0xFF) }
          0x53ACL -> { posOff = r.p; posLen = clen; pos = 0; for (i in 0 until clen) pos = (pos shl 8) or (b[r.p + i].toLong() and 0xFF) }
        }
        r.p += clen
      }
      if (sid >= 0 && pos >= 0) out += SeekEntry(sid, sidOff, sidLen, pos, posOff, posLen)
      r.p = seekEnd
    }
    return out
  }

  private fun computePatches(chain: Interceptor.Chain, request: Request): List<Patch> {
    val head = fetch(chain, request, 0, 64 * 1024)
    val r = Reader(head)
    if (readId(r) != 0x1A45DFA3L) return emptyList()
    r.p += readSize(r).toInt()
    if (readId(r) != 0x18538067L) return emptyList()
    readSize(r)
    val segmentDataStart = r.p.toLong()

    val first = seekEntries(head, r.p)
    if (first.any { it.id == 0x1C53BB6BL }) return emptyList() // already points at Cues
    val nested = first.firstOrNull { it.id == 0x114D9B74L } ?: return emptyList()
    if (nested.idLen != 4) return emptyList()

    val second = fetch(chain, request, segmentDataStart + nested.pos, 8 * 1024)
    val cues = seekEntries(second, 0).firstOrNull { it.id == 0x1C53BB6BL } ?: return emptyList()
    if (cues.pos >= (1L shl (8 * nested.posLen))) return emptyList()

    val posBytes = ByteArray(nested.posLen) { i -> ((cues.pos shr (8 * (nested.posLen - 1 - i))) and 0xFF).toByte() }
    return listOf(
      Patch(nested.idOffset.toLong(), byteArrayOf(0x1C, 0x53, 0xBB.toByte(), 0x6B)),
      Patch(nested.posOffset.toLong(), posBytes),
    )
  }

  /** Replaces bytes at absolute file offsets as the body streams through. */
  private class PatchingSource(source: BufferedSource, private var position: Long, private val patches: List<Patch>) :
    ForwardingSource(source) {
    override fun read(sink: Buffer, byteCount: Long): Long {
      val tmp = Buffer()
      val n = super.read(tmp, byteCount)
      if (n <= 0) return n
      val bytes = tmp.readByteArray()
      for (patch in patches) {
        for (i in patch.bytes.indices) {
          val abs = patch.offset + i
          if (abs >= position && abs < position + n) bytes[(abs - position).toInt()] = patch.bytes[i]
        }
      }
      position += n
      sink.write(bytes)
      return n
    }
  }
}

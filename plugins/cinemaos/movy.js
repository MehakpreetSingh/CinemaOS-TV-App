/**
 * CinemaOS - Movy (Nuvio QuickJS plugin).
 * Port of cinemaos-phone lib/scrapers/movy.ts (zetsu-core's movy.sx extractor).
 * api.wecollege.net hands out a per-title seed; each city shard returns its sources encrypted with
 * a seed-keyed stream cipher ("mvm1" magic). Playlists play with Movy's Origin/Referer headers.
 * No timers in the host: the phone app's 5 s / 8 s abort timeouts and the 1.2 s 429 back-off are
 * dropped (the host's 30 s OkHttp timeout applies; a 429 retries once immediately).
 */
var API = 'https://api.wecollege.net';
var SHARDS = ['miami', 'boise', 'atlanta', 'phoenix'];
var UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';
var SITE_HEADERS = { Origin: 'https://www.movy.sx', Referer: 'https://www.movy.sx/' };

var SLOTS = 61;
var MS = 2654435769; // 0x9E3779B9
var ACC_XOR = 2779096485; // 0xA5A359A5

function mix(l) {
  l = (l ^ (l >>> 16)) >>> 0;
  l = Math.imul(l, 2246822507) >>> 0;
  l = (l ^ (l >>> 13)) >>> 0;
  l = Math.imul(l, 3266489909) >>> 0;
  return (l ^ (l >>> 16)) >>> 0;
}

function fnvSeed(s) {
  var o = 2166136261;
  for (var i = 0; i < s.length; i++) o = Math.imul(o ^ s.charCodeAt(i), 16777619) >>> 0;
  return mix(o);
}

function rotl(v, sh) {
  return ((v << (sh & 31)) | (v >>> (32 - (sh & 31)))) >>> 0;
}

function CipherState(seed, tmdbId) {
  this.slots = new Uint32Array(SLOTS);
  this.present = new Uint8Array(SLOTS);
  var i = mix((fnvSeed(seed) ^ mix((tmdbId ^ MS) >>> 0)) >>> 0);
  for (var r = 0; r < 8; r++) {
    var n = i % SLOTS;
    i = rotl((i + MS) >>> 0, 7 + (r & 7));
    this.slots[n] = (i ^ mix(i)) >>> 0;
    this.present[n] = 1;
    i = mix((i + n) >>> 0);
  }
  this.acc = mix((i ^ ACC_XOR) >>> 0);
}

CipherState.prototype.next = function (offset) {
  var r = this.acc % SLOTS;
  var neg = this.present[r] ? 0xffffffff : 0;
  var x = (this.slots[r] ^ (Math.imul(MS, offset + 1) >>> 0)) >>> 0;
  var g = ((this.acc ^ x) | (this.acc & x & neg)) >>> 0;
  g = (rotl((g + this.acc) >>> 0, r & 31) ^ rotl(this.acc, (r * 7) & 31)) >>> 0;
  this.acc = mix((g + MS) >>> 0);
  this.slots[r] = this.acc;
  this.present[r] = 1;
  return this.acc;
};

function base64ToBytes(b64) {
  var bin = atob(b64);
  var out = new Uint8Array(bin.length);
  for (var i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function utf8(bytes, start) {
  var s = '';
  for (var i = start; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  try {
    return decodeURIComponent(escape(s));
  } catch (e) {
    return s;
  }
}

function decrypt(enc, seed, tmdbId) {
  var b64 = enc.trim().replace(/-/g, '+').replace(/_/g, '/');
  while (b64.length % 4) b64 += '=';
  var raw = base64ToBytes(b64);
  var st = new CipherState(seed, tmdbId);
  for (var idx = 0, off = 0; idx < raw.length; off++) {
    var d = st.next(off);
    for (var k = 0; k < 4 && idx < raw.length; k++) raw[idx++] ^= (d >>> (8 * k)) & 0xff;
  }
  if (String.fromCharCode(raw[0], raw[1], raw[2], raw[3]) !== 'mvm1') throw new Error('movy: bad payload');
  return utf8(raw, 4);
}

function qs(obj) {
  return Object.keys(obj)
    .map(function (k) {
      return encodeURIComponent(k) + '=' + encodeURIComponent(obj[k]);
    })
    .join('&');
}

async function getSeed(tmdbId, retry) {
  var headers = Object.assign({ 'User-Agent': UA }, SITE_HEADERS);
  var res = await fetch(API + '/seed?mediaId=' + tmdbId, { headers: headers });
  if (res.status === 429 && retry) return getSeed(tmdbId, false);
  if (!res.ok) return null;
  var j = await res.json();
  return (j && j.seed) || null;
}

// the API labels every link; only fall back to the URL (index-s1080p-...) when it doesn't
function qualityOf(url, q) {
  var m1 = String(q || '').match(/(\d{3,4})/);
  var m2 = url.match(/[-_/]s?(2160|1440|1080|720|480|360)p/);
  var n = (m1 && m1[1]) || (m2 && m2[1]);
  return n ? n + 'p' : 'Auto';
}
function rank(q) {
  return parseInt(q, 10) || 0;
}

async function tmdbInfo(type, id) {
  try {
    var res = await fetch(
      'https://api.themoviedb.org/3/' + (type === 'movie' ? 'movie' : 'tv') + '/' + id + '?api_key=' + TMDB_API_KEY + '&append_to_response=external_ids'
    );
    var d = await res.json();
    return {
      title: (d && (d.title || d.name)) || '',
      year: String((d && (d.release_date || d.first_air_date)) || '').slice(0, 4),
      imdbId: (d && ((d.external_ids && d.external_ids.imdb_id) || d.imdb_id)) || ''
    };
  } catch (e) {
    return { title: '', year: '', imdbId: '' };
  }
}

async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    var type = mediaType === 'movie' ? 'movie' : 'tv';
    var id = Number(tmdbId);
    var seed = id ? await getSeed(id, true) : null;
    if (!seed) return [];
    var info = await tmdbInfo(type, id);
    var params = qs({
      title: info.title,
      mediaType: type,
      year: info.year,
      tmdbId: String(id),
      imdbId: info.imdbId,
      seasonId: String(Number(season) || 1),
      episodeId: String(Number(episode) || 1),
      enc: '2',
      seed: seed
    });

    var results = await Promise.all(
      SHARDS.map(async function (shard) {
        try {
          var headers = Object.assign({ 'User-Agent': UA, Accept: 'application/json, text/plain, */*' }, SITE_HEADERS);
          var res = await fetch(API + '/' + shard + '/sources?' + params, { headers: headers });
          if (!res.ok) return null;
          return { shard: shard, data: JSON.parse(decrypt(await res.text(), seed, id)) };
        } catch (e) {
          return null;
        }
      })
    );

    var streams = [];
    var subtitles = [];
    var seen = {};
    results.forEach(function (r) {
      if (!r || !r.data) return;
      (r.data.sources || []).forEach(function (s) {
        var url = s.url || s.file;
        if (!url || seen[url]) return;
        seen[url] = true;
        streams.push({
          url: url,
          quality: qualityOf(url, s.quality),
          type: /\.mp4(\?|$)/.test(url) ? 'mp4' : 'hls',
          shard: r.shard
        });
      });
      // shards name the list and its fields differently (subtitles/captions/tracks; url/file/src)
      var lists = [r.data.subtitles, r.data.captions, r.data.tracks, r.data.subs].filter(Array.isArray);
      var subList = lists.length ? lists[0] : [];
      subList.forEach(function (sub) {
        var subUrl = typeof sub === 'string' ? sub : sub && (sub.url || sub.file || sub.src);
        if (!subUrl || seen[subUrl] || (sub && sub.kind && !/caption|subtitle/i.test(sub.kind))) return;
        seen[subUrl] = true;
        var lang = String((sub && (sub.lang || sub.language || sub.srclang || sub.label)) || 'en').toLowerCase();
        subtitles.push({ url: subUrl, language: lang, name: (sub && (sub.name || sub.label)) || lang.toUpperCase() });
      });
    });
    streams.sort(function (a, b) {
      return rank(b.quality) - rank(a.quality);
    });

    var headers = Object.assign({ 'User-Agent': UA }, SITE_HEADERS);
    return streams.map(function (st) {
      return {
        name: st.quality + ' · ' + st.type.toUpperCase(),
        title: 'Movy · ' + st.quality + ' · ' + st.shard,
        url: st.url,
        quality: st.quality,
        provider: 'Movy',
        type: st.type,
        headers: headers,
        subtitles: subtitles
      };
    });
  } catch (err) {
    console.error('movy error: ' + (err && err.message ? err.message : err));
    return [];
  }
}

module.exports = { getStreams: getStreams };

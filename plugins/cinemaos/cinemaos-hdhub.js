/**
 * CinemaOS - HDHub (Nuvio QuickJS plugin).
 * Port of the phone app's lib/scrapers/hdhub.ts: calls the HDHub Stremio addon stream endpoint (keyed by IMDb id),
 * drops "Download Only" mirrors, Range-probes every link (dead / HTML / JSON answers are dropped) and sorts
 * 1080p first, then 720p, then 4K, then the rest. Movies and TV only (anime ids return []).
 * Host notes: no timers, so probes run in small parallel batches under a total time budget.
 */
var BASE = 'https://hdhub.thevolecitor.qzz.io/eyJ0b3Jib3giOiJ1bnNldCIsInF1YWxpdGllcyI6IjIxNjBwLDEwODBwLDcyMHAiLCJzb3J0IjoiZGVzYyJ9';
var TMDB_PROXY = 'https://cinemaos.tech/api/tmdb';
var BUDGET_MS = 18000;
var BATCH = 8;
var MAX_CANDIDATES = 24;

async function imdbOf(type, id) {
  var ref = 'https://cinemaos.tech/' + (type === 'movie' ? 'movie' : 'tv') + '/' + id;
  var res = await fetch(TMDB_PROXY + '?requestID=' + (type === 'movie' ? 'movieData' : 'tvData') + '&id=' + id + '&language=en-US', {
    headers: {
      Accept: '*/*',
      'Accept-Language': 'en-IN,en;q=0.9',
      'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15',
      Referer: ref,
      'Sec-Fetch-Site': 'same-origin',
      'Sec-Fetch-Mode': 'cors',
      'Sec-Fetch-Dest': 'empty'
    }
  });
  if (!res.ok) return '';
  var d = await res.json();
  return (d && ((d.external_ids && d.external_ids.imdb_id) || d.imdb_id)) || '';
}

var animeCache = {};
// True when arm.haglund.dev maps this TMDB tv id to an anime entry (those belong to the anime plugins).
async function isAnimeTmdb(id) {
  if (animeCache[id] !== undefined) return animeCache[id];
  var v = false;
  try {
    var r = await fetch('https://arm.haglund.dev/api/v2/themoviedb?id=' + id);
    if (r.ok) { var j = await r.json(); v = Array.isArray(j) && j.length > 0; }
  } catch (e) { v = false; }
  animeCache[id] = v;
  return v;
}

async function isVideo(url, headers) {
  try {
    var h = {};
    var HDRS = headers || {};
    Object.keys(HDRS).forEach(function (k) { h[k] = HDRS[k]; });
    h.Range = 'bytes=0-63';
    var r = await fetch(url, { headers: h });
    var ct = (r.headers && r.headers.get && r.headers.get('content-type')) || '';
    if (!r.ok || /text\/html|application\/json/i.test(ct)) return false;
    // server ignored Range (200): never read the body (could be GBs) - judge by headers alone
    if (r.status === 200) return false; // no Range support: ExoPlayer cannot seek / read MKV cues
    var b = new Uint8Array(await r.arrayBuffer());
    var mkv = b[0] === 0x1a && b[1] === 0x45 && b[2] === 0xdf && b[3] === 0xa3;
    var mp4 = b[4] === 0x66 && b[5] === 0x74 && b[6] === 0x79 && b[7] === 0x70;
    if (!(mkv || mp4 || /^video\//i.test(ct))) return false;
    // ExoPlayer reads the END of MKV/MP4 (cues / moov): hosts that 403 a tail Range are unplayable
    var cr = (r.headers.get('content-range') || '').match(/\/(\d+)$/);
    var total = cr ? parseInt(cr[1], 10) : 0;
    if (total > 2097152) {
      var t = {};
      Object.keys(HDRS).forEach(function (k) { t[k] = HDRS[k]; });
      t.Range = 'bytes=' + (total - 1048576) + '-'; // open-ended like ExoPlayer, last 1 MB
      var tr = await fetch(url, { headers: t });
      if (tr.status !== 206 && tr.status !== 200) return false;
    }
    return true;
  } catch (e) {
    return false;
  }
}

function qualityOf(text) {
  if (/2160p|\b4k\b|uhd/i.test(text)) return 2160;
  var m = text.match(/(\d{3,4})p/i);
  return m ? parseInt(m[1], 10) : 0;
}

async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    var id = String(tmdbId || '').trim();
    if (/^(kitsu|anilist|mal|myanimelist|anidb):/i.test(id)) return []; // anime ids belong to the anime plugins
    if (!/^\d+$/.test(id)) return []; // anime / non-TMDB ids belong to the anime plugins
    var type = mediaType === 'movie' ? 'movie' : 'tv';
    var both = await Promise.all([imdbOf(type, id), type === 'tv' ? isAnimeTmdb(id) : Promise.resolve(false)]);
    if (both[1]) return []; // TMDB tv show that is anime -> anime plugins only
    var imdb = both[0];
    if (!imdb) return [];
    var s = Number(season) || 1, e = Number(episode) || 1;
    var path = type === 'movie' ? 'movie/' + imdb : 'series/' + imdb + '%3A' + s + '%3A' + e;
    var res = await fetch(BASE + '/stream/' + path + '.json');
    if (!res.ok) return [];
    var json = await res.json();
    var out = [];
    (json && json.streams || []).forEach(function (st) {
      if (!st || !st.url || st.externalUrl) return;
      var desc = String(st.description || st.title || '');
      var lines = desc.split('\n');
      var n = qualityOf(st.name + ' ' + desc);
      var sz = desc.match(/💾\s*([\d.]+\s*[GMK]B)/);
      var hm = lines[0].match(/^\[([^\]]+)\]/);
      var host = (hm ? hm[1] : (lines[lines.length - 1] || '').split('|')[0] || 'HDHub').trim();
      var file = (lines[0].replace(/^(\[[^\]]*\]\s*)+/, '') || st.name || '').trim();
      var bh = st.behaviorHints && st.behaviorHints.proxyHeaders && st.behaviorHints.proxyHeaders.request;
      out.push({ url: st.url, n: n, quality: n ? (n >= 2160 ? '4K' : n + 'p') : 'HD', label: file, size: sz ? sz[1] : '', host: host, headers: bh || {} });
    });
    var cands = out.filter(function (x) { return !/download only/i.test(x.host + ' ' + x.label); });
    // probe the most useful candidates first so the budget is spent on likely winners
    var rank = function (q) { return q === 1080 ? 0 : q === 720 ? 1 : q >= 2160 ? 2 : 3; };
    cands.sort(function (a, b) { return rank(a.n) - rank(b.n); });
    cands = cands.slice(0, MAX_CANDIDATES);
    var t0 = Date.now(), ok = [];
    for (var i = 0; i < cands.length; i += BATCH) {
      if (Date.now() - t0 > BUDGET_MS) break;
      var batch = cands.slice(i, i + BATCH);
      var r = await Promise.all(batch.map(function (c) { return isVideo(c.url, c.headers); }));
      batch.forEach(function (c, k) { if (r[k]) ok.push(c); });
    }
    ok.sort(function (a, b) { return rank(a.n) - rank(b.n); });
    return ok.map(function (x) {
      return {
        name: x.quality + (x.size ? ' · ' + x.size : ''),
        title: 'HDHub · ' + x.host + ' · ' + x.label,
        url: x.url,
        quality: x.quality,
        size: x.size,
        provider: 'HDHub',
        type: /\.mp4(\?|&|$)/i.test(x.url) ? 'mp4' : 'mkv',
        headers: x.headers
      };
    });
  } catch (err) {
    console.log('[cinemaos-hdhub] ' + (err && err.message ? err.message : err));
    return [];
  }
}

module.exports = { getStreams: getStreams };

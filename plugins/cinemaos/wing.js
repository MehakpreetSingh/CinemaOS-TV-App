/**
 * CinemaOS - Wing Downloads (Nuvio QuickJS plugin).
 * Port of the CinemaOS web app download feature (app/api/download). Calls the upstream
 * https://downloads2.wing.st directly (no API key). Returns direct-download file links
 * (MKV/MP4, multi-GB, multi-language audio) sorted by quality.
 */
var API = 'https://downloads2.wing.st';

function qualityLabel(q) {
  var n = parseInt(q, 10) || 0;
  if (n >= 2160) return '2160p';
  if (n >= 1440) return '1440p';
  if (n >= 1080) return '1080p';
  if (n >= 720) return '720p';
  if (n >= 480) return '480p';
  return n ? n + 'p' : 'Unknown';
}

function langsOf(name) {
  var found = [];
  var map = { Hindi: 'Hindi', English: 'English', Tamil: 'Tamil', Telugu: 'Telugu', Malayalam: 'Malayalam', Kannada: 'Kannada', Bengali: 'Bengali', Japanese: 'Japanese', Korean: 'Korean', Spanish: 'Spanish', French: 'French' };
  Object.keys(map).forEach(function (k) {
    if (new RegExp('\\b' + k + '\\b', 'i').test(name)) found.push(map[k]);
  });
  return found;
}

// a.111477.xyz 307s to an HTML "bulk download" page (403 to players) - never playable.
var BAD_HOST = /^https?:\/\/(a|p)\.111477\.xyz\//i;

// Range-probe a link: only a 200/206 whose first bytes look like MKV/MP4 (or a video/* type) counts as playable.
async function isVideo(url) {
  try {
    var r = await fetch(url, { headers: { Range: 'bytes=0-63' } });
    var ct = (r.headers && r.headers.get && r.headers.get('content-type')) || '';
    if (!r.ok || /text\/html|application\/json/i.test(ct)) return false;
    // server ignored Range (200): never read the body (could be GBs) - judge by headers alone
    if (r.status === 200) return /^video\/|octet-stream/i.test(ct);
    var b = new Uint8Array(await r.arrayBuffer());
    var mkv = b[0] === 0x1a && b[1] === 0x45 && b[2] === 0xdf && b[3] === 0xa3;
    var mp4 = b[4] === 0x66 && b[5] === 0x74 && b[6] === 0x79 && b[7] === 0x70;
    return mkv || mp4 || /^video\//i.test(ct);
  } catch (e) {
    return false;
  }
}

async function getStreams(tmdbId, mediaType, season, episode) {
  if (/^(kitsu|anilist|mal|myanimelist|anidb):/i.test(String(tmdbId || ''))) return []; // anime ids belong to the anime plugins
  try {
    var id = String(tmdbId || '').trim();
    if (!/^\d+$/.test(id)) return [];
    var path;
    if (mediaType === 'movie') {
      path = 'movie/' + id;
    } else {
      var s = Number(season) || 1;
      var e = Number(episode) || 1;
      path = 'tv/' + id + '/' + s + '/' + e;
    }
    var res = await fetch(API + '/' + path, { headers: { Accept: 'application/json' } });
    if (!res.ok) return [];
    var data = await res.json();
    var links = Array.isArray(data && data.links) ? data.links : [];
    var out = [];
    var seen = {};
    links.forEach(function (l) {
      if (!l || typeof l.url !== 'string' || !/^https?:\/\//i.test(l.url) || seen[l.url]) return;
      seen[l.url] = true;
      var q = qualityLabel(l.quality);
      var name = String(l.name || l.source || '');
      var langs = langsOf(name);
      out.push({
        name: q + (l.size ? ' · ' + l.size : ''),
        title: 'Wing · ' + (l.source || l.provider || '') + ' · ' + name,
        url: l.url,
        quality: q,
        size: l.size || '',
        language: langs.join(', '),
        provider: 'Wing',
        type: /\.mp4(\?|&|$)/i.test(l.url) ? 'mp4' : 'mkv',
        headers: {}
      });
    });
    out.sort(function (a, b) {
      return (parseInt(b.quality, 10) || 0) - (parseInt(a.quality, 10) || 0);
    });
    out = out.filter(function (x) { return !BAD_HOST.test(x.url); });
    // Probe the top candidates in parallel; no timers, so resolve as soon as 8 are verified (or all settled).
    var cands = out.slice(0, 24);
    var verified = await new Promise(function (resolve) {
      var okList = [], done = 0, fin = false;
      function finish() { if (!fin) { fin = true; resolve(okList.slice()); } }
      if (!cands.length) return finish();
      cands.forEach(function (c) {
        isVideo(c.url).then(function (ok) {
          done++;
          if (ok) okList.push(c);
          if (okList.length >= 4 || done >= cands.length) finish();
        });
      });
    });
    verified.sort(function (a, b) {
      return (parseInt(b.quality, 10) || 0) - (parseInt(a.quality, 10) || 0);
    });
    return verified;
  } catch (err) {
    console.error('wing error: ' + (err && err.message ? err.message : err));
    return [];
  }
}

module.exports = { getStreams: getStreams };

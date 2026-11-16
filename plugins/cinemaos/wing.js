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

async function getStreams(tmdbId, mediaType, season, episode) {
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
    return out;
  } catch (err) {
    console.error('wing error: ' + (err && err.message ? err.message : err));
    return [];
  }
}

module.exports = { getStreams: getStreams };

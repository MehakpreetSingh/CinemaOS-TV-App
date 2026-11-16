/**
 * CinemaOS - MegaPlay anime (Nuvio QuickJS plugin).
 * Port of the web app lib/anime-megaplay.ts. Flow:
 *  id (kitsu:/anilist:/mal:/tmdb) -> arm.haglund.dev -> MAL/AniList id
 *  -> megaplay.buzz /stream/{mal|ani}/{id}/{ep}/{sub|dub} (<title>File N) -> /stream/getSources
 *  -> POST enc to enc-dec.app/api/dec-megaplay -> master m3u8 (+ tracks as subtitles).
 * Host notes: no timers, 1 MB response cap, Content-Type capitalised on POST. Anime ids arrive as
 * "kitsu:12" with season null and an ABSOLUTE episode; TMDB ids arrive numeric with season/episode.
 */
var ARM = 'https://arm.haglund.dev/api/v2';
var MEGAPLAY = 'https://megaplay.buzz';
var DECRYPT_URL = 'https://enc-dec.app/api/dec-megaplay';
var UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36';
var MP_HEADERS = { 'User-Agent': UA, Origin: MEGAPLAY, Referer: MEGAPLAY + '/', 'X-Requested-With': 'XMLHttpRequest' };
var STREAM_HEADERS = { 'User-Agent': UA, Origin: MEGAPLAY, Referer: MEGAPLAY + '/' };

async function getJson(url, headers) {
  var res = await fetch(url, { headers: headers || { Accept: 'application/json' } });
  if (!res.ok) throw new Error('HTTP ' + res.status + ' ' + url);
  return await res.json();
}

function langFromLabel(label) {
  var l = String(label || '').toLowerCase();
  var map = { english: 'en', japanese: 'ja', spanish: 'es', portuguese: 'pt', french: 'fr', german: 'de', italian: 'it', arabic: 'ar', russian: 'ru', indonesian: 'id', chinese: 'zh', korean: 'ko', hindi: 'hi', thai: 'th', vietnamese: 'vi' };
  var keys = Object.keys(map);
  for (var i = 0; i < keys.length; i++) if (l.indexOf(keys[i]) === 0) return map[keys[i]];
  return l.slice(0, 2) || 'en';
}

/** -> [{ mal, anilist, episode }] candidates, best first. Empty when the id cannot be mapped. */
async function resolveTargets(tmdbId, mediaType, season, episode) {
  var id = String(tmdbId || '').trim();
  var ep = Number(episode) || 1;
  var m = id.match(/^(kitsu|anilist|mal|myanimelist):(\d+)/i);
  if (m) {
    var src = m[1].toLowerCase() === 'mal' ? 'myanimelist' : m[1].toLowerCase();
    var r = await getJson(ARM + '/ids?source=' + src + '&id=' + m[2]);
    if (!r || (!r.myanimelist && !r.anilist)) return [];
    return [{ mal: r.myanimelist, anilist: r.anilist, episode: ep }];
  }
  if (/^\d+$/.test(id)) {
    // TMDB id: arm's /themoviedb route returns every anime entry mapped to it (per season/cour).
    var list;
    try { list = await getJson(ARM + '/themoviedb?id=' + id); } catch (e) { return []; }
    if (!Array.isArray(list)) return [];
    var out = [];
    if (mediaType === 'movie') {
      list.forEach(function (e) { if (e && e.media === 'MOVIE' && (e.myanimelist || e.anilist)) out.push({ mal: e.myanimelist, anilist: e.anilist, episode: 1 }); });
      return out;
    }
    var s = Number(season) || 1;
    list.forEach(function (e) {
      if (!e || !(e.myanimelist || e.anilist)) return;
      if (e.media !== 'TV' && e.media !== 'ONA') return;
      if (e['themoviedb-season'] === s) out.push({ mal: e.myanimelist, anilist: e.anilist, episode: ep });
    });
    return out;
  }
  return [];
}

async function fetchOne(t, category) {
  var path = t.mal ? 'mal/' + t.mal : t.anilist ? 'ani/' + t.anilist : '';
  if (!path) return null;
  var page = await fetch(MEGAPLAY + '/stream/' + path + '/' + t.episode + '/' + category, { headers: MP_HEADERS });
  if (!page.ok) return null;
  var html = await page.text();
  var mm = html.match(/<title>File (\d+) - MegaPlay<\/title>/);
  if (!mm) return null;
  var payload = await getJson(MEGAPLAY + '/stream/getSources?id=' + mm[1] + '&platform=OTHER', MP_HEADERS);
  if (!payload || typeof payload.enc !== 'string') return null;
  var dres = await fetch(DECRYPT_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ text: payload.enc })
  });
  if (!dres.ok) return null;
  var dec = await dres.json();
  if (!dec || dec.status !== 200) return null;
  var merged = {};
  Object.keys(payload).forEach(function (k) { if (k !== 'enc') merged[k] = payload[k]; });
  if (dec.result && typeof dec.result === 'object') Object.keys(dec.result).forEach(function (k) { merged[k] = dec.result[k]; });
  var file = null;
  if (typeof merged.file === 'string') file = merged.file;
  else if (typeof merged.sources === 'string') file = merged.sources;
  else if (merged.sources && merged.sources.file) file = merged.sources.file;
  else if (Array.isArray(merged.sources) && merged.sources[0]) file = merged.sources[0].file || merged.sources[0].url || null;
  if (!file) return null;
  var raw = Array.isArray(merged.tracks) ? merged.tracks : Array.isArray(merged.subtitles) ? merged.subtitles : [];
  var subs = [];
  raw.forEach(function (tr) {
    var u = tr && (tr.file || tr.url);
    if (!u || (tr.kind || 'captions') === 'thumbnails') return;
    var label = String(tr.label || tr.lang || 'Subtitle');
    subs.push({ id: 'megaplay-' + category + '-' + subs.length, url: String(u), lang: langFromLabel(label), label: label, headers: STREAM_HEADERS });
  });
  return { url: file, subtitles: subs, category: category };
}

async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    var targets = await resolveTargets(tmdbId, mediaType, season, episode);
    for (var i = 0; i < targets.length; i++) {
      var results = await Promise.all(['sub', 'dub'].map(function (c) { return fetchOne(targets[i], c).catch(function () { return null; }); }));
      var out = [];
      results.forEach(function (r) {
        if (!r) return;
        var tag = r.category === 'sub' ? 'SUB' : 'DUB';
        out.push({
          name: 'MegaPlay · ' + tag,
          title: 'MegaPlay · ' + tag + ' · HLS',
          url: r.url,
          quality: 'Auto',
          language: r.category === 'sub' ? 'Japanese' : 'English',
          provider: 'MegaPlay',
          type: 'hls',
          headers: STREAM_HEADERS,
          subtitles: r.subtitles
        });
      });
      if (out.length) return out;
    }
    return [];
  } catch (e) {
    console.log('[cinemaos-megaplay] ' + (e && e.message ? e.message : e));
    return [];
  }
}

module.exports = { getStreams: getStreams };

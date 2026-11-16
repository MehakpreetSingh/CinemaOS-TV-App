/**
 * CinemaOS - Castle (Nuvio QuickJS plugin).
 * Port of cinemaos-phone lib/scrapers/castle.ts (vega-providers/castle). Encrypted JSON API
 * (AES-128-CBC, key = base64-decoded securityKey + "T!BgJB", IV = key); each audio language is a
 * separate video, 480p-1080p HLS. The phone app's hand-written AES is replaced by crypto-js.
 */
var CryptoJS = require('crypto-js');

var API = 'https://api.hlowb.com';
var CHANNEL = 'IndiaA';
var LANG = 'en-US';
var PACKAGE = 'com.external.castle';
var APK_SIGN = 'ED0955EB04E67A1D9F3305B95454FED485261475';
var UA = 'okhttp/4.9.0';
var SUFFIX = 'T!BgJB';
var SERIES_TYPES = { '1': true, '3': true, '5': true };
var QUALITY = { '3': '1080p', '2': '720p', '1': '480p' };
var RESOLUTIONS = ['3', '2', '1'];

/** AES-128 key = decode_b64(securityKey) + "T!BgJB", padded/truncated to 16 bytes. */
function deriveKey(securityKeyB64) {
  var bin = atob(securityKeyB64);
  var bytes = [];
  var i;
  for (i = 0; i < bin.length && bytes.length < 16; i++) bytes.push(bin.charCodeAt(i) & 0xff);
  for (i = 0; i < SUFFIX.length && bytes.length < 16; i++) bytes.push(SUFFIX.charCodeAt(i) & 0xff);
  while (bytes.length < 16) bytes.push(0);
  var hex = bytes
    .map(function (b) {
      return ('0' + b.toString(16)).slice(-2);
    })
    .join('');
  return CryptoJS.enc.Hex.parse(hex);
}

/** Decrypt an AES-128-CBC / PKCS7 base64 payload. Returns the UTF-8 plaintext. */
function decryptCBC(cipherB64, key) {
  var cleaned = String(cipherB64).replace(/\s+/g, '');
  var dec = CryptoJS.AES.decrypt({ ciphertext: CryptoJS.enc.Base64.parse(cleaned) }, key, {
    iv: key,
    mode: CryptoJS.mode.CBC,
    padding: CryptoJS.pad.Pkcs7
  });
  var text = dec.toString(CryptoJS.enc.Utf8);
  if (!text) throw new Error('castle: AES decrypt produced empty output');
  return text;
}

/** Wraps 16+ digit IDs in quotes so JSON.parse does not lose precision. */
function castleSafeParse(text) {
  var cleaned = String(text).replace(/"([A-Za-z0-9_]*Id|id)"\s*:\s*(-?\d+)/g, '"$1":"$2"');
  return JSON.parse(cleaned);
}

var cachedKey = null;
var cachedKeyAt = 0;
async function securityKey() {
  if (cachedKey && Date.now() - cachedKeyAt < 5 * 60000) return cachedKey;
  var res = await fetch(API + '/v0.1/system/getSecurityKey/1?channel=' + CHANNEL + '&clientType=1&lang=' + LANG, { headers: { 'User-Agent': UA } });
  var data = await res.json();
  if (!data || data.code !== 200 || !data.data) throw new Error('castle: no security key');
  cachedKey = deriveKey(data.data);
  cachedKeyAt = Date.now();
  return cachedKey;
}

async function unwrap(text) {
  var cipher = text.trim();
  try {
    var j = JSON.parse(text);
    if (j && j.data && typeof j.data === 'object') return j;
    if (j && typeof j.data === 'string') cipher = j.data;
  } catch (e) {}
  return castleSafeParse(decryptCBC(cipher, await securityKey()));
}

async function get(path) {
  var res = await fetch(API + path, { headers: { 'User-Agent': UA } });
  return unwrap(await res.text());
}
async function post(path, body) {
  var res = await fetch(API + path, {
    method: 'POST',
    headers: { 'User-Agent': UA, 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(body)
  });
  return unwrap(await res.text());
}

var common = 'channel=' + CHANNEL + '&clientType=1&lang=' + LANG + '&packageName=' + PACKAGE;
function search(q) {
  return get('/film-api/v1.1.0/movie/searchByKeyword?' + common + '&keyword=' + encodeURIComponent(q) + '&mode=1&page=1&size=30');
}
async function details(movieId) {
  var env = await get('/film-api/v1.9.9/movie?' + common + '&movieId=' + encodeURIComponent(movieId));
  var outer = (env && env.data) || {};
  var inner = outer.data;
  return inner && typeof inner === 'object' && (inner.title || inner.episodes || inner.seasons || inner.id) ? inner : outer;
}
function video(movieId, episodeId, resolution, languageId) {
  var body = {
    mode: '1',
    appMarket: 'GuanWang',
    clientType: '1',
    woolUser: 'false',
    apkSignKey: APK_SIGN,
    androidVersion: '13',
    movieId: movieId,
    episodeId: episodeId,
    isNewUser: 'true',
    resolution: resolution,
    packageName: PACKAGE
  };
  if (languageId) body.languageId = languageId;
  return post('/film-api/v2.0.1/movie/getVideo2?clientType=1&packageName=' + PACKAGE + '&channel=' + CHANNEL + '&lang=' + LANG, body);
}

function norm(s) {
  return s.toLowerCase().replace(/&/g, 'and').replace(/[^a-z0-9]+/g, '');
}

async function tmdbDetails(type, id) {
  try {
    var res = await fetch('https://api.themoviedb.org/3/' + (type === 'movie' ? 'movie' : 'tv') + '/' + id + '?api_key=' + TMDB_API_KEY);
    return await res.json();
  } catch (e) {
    return null;
  }
}

async function castle(type, title, year, season, episode) {
  if (!title) return [];
  var env = await search(title);
  var rows = (env && env.data && (env.data.rows || (env.data.data && env.data.data.rows))) || [];
  var want = norm(title);
  var candidates = rows.filter(function (r) {
    var t = norm(String(r.title || r.name || '').replace(/\s*\([^)]*\)\s*$/, '')); // "(2010)", "(Reloaded Version)"
    var isSeries = !!SERIES_TYPES[String(r.movieType)];
    // series rows are often titled "Name Season 2"; compare on the base name
    return (type === 'tv') === isSeries && (t === want || (type === 'tv' && t.replace(/season\d+$/, '') === want));
  });
  if (!candidates.length) return [];
  function yearOf(r) {
    return r.publishTime ? new Date(Number(r.publishTime)).getFullYear() : r.year ? Number(r.year) : 0;
  }
  candidates.sort(function (a, b) {
    return Math.abs(yearOf(a) - Number(year || 0)) - Math.abs(yearOf(b) - Number(year || 0));
  });

  var first = candidates[0];
  var d = await details(String(first.id != null ? first.id : first.redirectId));
  if (type === 'tv') {
    var seasons = Array.isArray(d.seasons) ? d.seasons : [];
    var s = seasons.find(function (x) {
      return Number(x.number) === season;
    });
    if (s && s.movieId && String(s.movieId) !== String(d.id)) d = await details(String(s.movieId));
    else if (seasons.length > 1 && !s) return [];
  }
  var episodes = Array.isArray(d.episodes) ? d.episodes : [];
  var ep =
    type === 'tv'
      ? episodes.find(function (x, i) {
          return Number(x.number != null ? x.number : i + 1) === episode;
        })
      : episodes[0];
  if (!ep || !ep.id) return [];

  var tracks = Array.isArray(ep.tracks) ? ep.tracks : [];
  var perLang = tracks.some(function (t) {
    return t && t.existIndividualVideo === true;
  });
  var langs = perLang
    ? tracks
        .filter(function (t) {
          return t && t.languageId != null;
        })
        .map(function (t) {
          return { id: String(t.languageId), name: t.languageName || t.abbreviate || 'Unknown' };
        })
    : [{ id: null, name: tracks.length > 1 ? 'Multi-audio' : (tracks[0] && tracks[0].languageName) || 'Original' }];

  var seen = {};
  var out = [];
  var jobs = [];
  langs.forEach(function (l) {
    RESOLUTIONS.forEach(function (res) {
      jobs.push(
        (async function () {
          try {
            var env2 = await video(String(d.id), String(ep.id), res, l.id);
            var v = (env2 && env2.data && (env2.data.data || env2.data)) || {};
            var url = v.videoUrl;
            if (!url || v.permissionDenied === true || /preview/i.test(url) || seen[url]) return;
            seen[url] = true;
            out.push({ url: url, quality: QUALITY[res], language: l.name, type: /\.m3u8(\?|#|$)/i.test(url) ? 'hls' : 'mp4' });
          } catch (e) {}
        })()
      );
    });
  });
  await Promise.all(jobs);
  return out.sort(function (a, b) {
    return parseInt(b.quality, 10) - parseInt(a.quality, 10);
  });
}

async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    var type = mediaType === 'movie' ? 'movie' : 'tv';
    var tmdb = await tmdbDetails(type, tmdbId);
    var title = (tmdb && (tmdb.title || tmdb.name)) || '';
    var year = String((tmdb && (tmdb.release_date || tmdb.first_air_date)) || '').slice(0, 4);
    var list = await castle(type, title, year, Number(season) || 1, Number(episode) || 1);
    return list.map(function (st) {
      return {
        name: st.quality + ' · ' + st.type.toUpperCase(),
        title: 'Castle · ' + st.language + ' · ' + st.quality,
        url: st.url,
        quality: st.quality,
        language: st.language,
        provider: 'Castle',
        type: st.type,
        headers: { 'User-Agent': UA }
      };
    });
  } catch (err) {
    console.error('castle error: ' + (err && err.message ? err.message : err));
    return [];
  }
}

module.exports = { getStreams: getStreams };

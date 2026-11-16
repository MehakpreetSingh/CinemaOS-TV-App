/**
 * CinemaOS - MovieBox (Nuvio QuickJS plugin).
 * Port of cinemaos-phone lib/scrapers/moviebox.ts (MovieBox v10):
 *  1. H5 play endpoint (movie-box.co) - real MP4 streams + DASH manifests
 *  2. resourceDetectors in the mobile subject detail - direct MP4 links
 *  3. mobile play-info - host rotation + alt key, decoy CDN paths rejected
 * Uses the host's crypto-js, fetch and TMDB_API_KEY globals. No timers exist in the host,
 * so per-request timeouts rely on the host's 30 s OkHttp timeout and the 60 s plugin cap.
 */
var CryptoJS = require('crypto-js');

var HOSTS = [
  'https://api3.aoneroom.com',
  'https://api6.aoneroom.com',
  'https://api5.aoneroom.com',
  'https://api4.aoneroom.com',
  'https://api7.aoneroom.com',
  'https://api4sg.aoneroom.com',
  'https://apig.inmoviebox.com',
  'https://api.inmoviebox.com',
  'https://api-in.inmoviebox.com'
];
var KEY_DEFAULT = 'NzZpUmwwN3MweFNOOWpxbUVXQXQ3OUVCSlp1bElRSXNWNjRGWnIyTw==';
var KEY_ALT = 'WHFuMm5uTzQxL0w5Mm8xaXVYaFNMSFRiWHZZNFo1Wlo2Mm04bVNMQQ==';
var APP_UA = 'com.community.mbox.in/50020130 (Linux; U; Android 14; en_IN; SM-S918B; Build/UD1A.230803.041; Cronet/145.0.7582.0)';
var REFERER = 'https://moviebox.ph/';
var H5_BASE = 'https://movie-box.co';
var H5_UA = 'Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Mobile Safari/537.36';

function keyOf(b64) {
  return CryptoJS.enc.Base64.parse(CryptoJS.enc.Base64.parse(b64).toString(CryptoJS.enc.Utf8));
}
var SECRET_DEFAULT = keyOf(KEY_DEFAULT);
var SECRET_ALT = keyOf(KEY_ALT);

var DEVICE_ID = (function () {
  var s = '';
  for (var i = 0; i < 32; i++) s += '0123456789abcdef'[Math.floor(Math.random() * 16)];
  return s;
})();

function md5Hex(s) {
  return CryptoJS.MD5(s).toString(CryptoJS.enc.Hex);
}

function dec(s) {
  try {
    return decodeURIComponent(s.replace(/\+/g, ' '));
  } catch (e) {
    return s;
  }
}

function pathAndPairs(url) {
  var m = url.match(/^https?:\/\/[^/?#]+([^?#]*)(?:\?([^#]*))?/i);
  var path = (m && m[1]) || '/';
  var pairs = ((m && m[2]) || '')
    .split('&')
    .filter(Boolean)
    .map(function (p) {
      var i = p.indexOf('=');
      return i < 0 ? [dec(p), ''] : [dec(p.slice(0, i)), dec(p.slice(i + 1))];
    });
  return { path: path, pairs: pairs };
}

function b64UrlDecode(s) {
  var b = s.replace(/-/g, '+').replace(/_/g, '/');
  b += '='.repeat((4 - (b.length % 4)) % 4);
  return CryptoJS.enc.Base64.parse(b).toString(CryptoJS.enc.Utf8);
}

// ---- signing ----------------------------------------------------------------

function canonicalString(method, contentType, url, body, ts) {
  var pp = pathAndPairs(url);
  var seenKeys = {};
  var keys = [];
  pp.pairs.forEach(function (p) {
    if (!seenKeys[p[0]]) {
      seenKeys[p[0]] = true;
      keys.push(p[0]);
    }
  });
  keys.sort();
  var query = keys
    .map(function (k) {
      return pp.pairs
        .filter(function (p) {
          return p[0] === k;
        })
        .map(function (p) {
          return k + '=' + p[1];
        })
        .join('&');
    })
    .join('&');
  var canonicalUrl = query ? pp.path + '?' + query : pp.path;
  var bodyHash = '';
  var bodyLen = '';
  if (body != null) {
    var words = CryptoJS.enc.Utf8.parse(body);
    bodyLen = String(words.sigBytes);
    bodyHash = md5Hex(words);
  }
  return method.toUpperCase() + '\napplication/json\n' + contentType + '\n' + bodyLen + '\n' + ts + '\n' + bodyHash + '\n' + canonicalUrl;
}

function signature(method, contentType, url, body, ts, alt) {
  var mac = CryptoJS.HmacMD5(canonicalString(method, contentType, url, body, ts), alt ? SECRET_ALT : SECRET_DEFAULT).toString(CryptoJS.enc.Base64);
  return ts + '|2|' + mac;
}

var CLIENT_INFO = JSON.stringify({
  package_name: 'com.community.mbox.in',
  version_name: '4.0.03.0920.03',
  version_code: 50020130,
  os: 'android',
  os_version: '14',
  device_id: DEVICE_ID,
  install_store: 'official',
  gaid: '1b2212c1-dadf-43c3-a0c8-bd6ce48ae22d',
  brand: 'samsung',
  model: 'SM-S918B',
  system_language: 'en',
  net: 'NETWORK_WIFI',
  region: 'IN',
  timezone: 'Asia/Calcutta',
  sp_code: ''
});

function buildHeaders(url, o) {
  var method = o.method || 'GET';
  var ts = Date.now();
  var contentType = method === 'POST' ? 'application/json; charset=utf-8' : 'application/json';
  var h = {
    'User-Agent': APP_UA,
    Accept: 'application/json',
    // Capitalised: the host picks the POST body media type from "Content-Type" (case-sensitive lookup)
    'Content-Type': contentType,
    'x-client-token': ts + ',' + md5Hex(String(ts).split('').reverse().join('')),
    'x-tr-signature': signature(method, contentType, url, o.body != null ? o.body : null, ts, !!o.alt),
    'x-client-info': CLIENT_INFO,
    'x-client-status': '0'
  };
  if (o.details) h['x-play-mode'] = '2';
  if (o.playback) h['x-play-mode'] = '1';
  if (o.token) h['authorization'] = 'Bearer ' + o.token;
  return h;
}

// ---- token ------------------------------------------------------------------

var cachedToken = null;

function jwtExpiry(token) {
  try {
    return Number(JSON.parse(b64UrlDecode(token.split('.')[1])).exp) * 1000 || 0;
  } catch (e) {
    return 0;
  }
}
function tokenValid(t) {
  return !!t && jwtExpiry(t) > Date.now() + 60000;
}

async function getToken() {
  if (tokenValid(cachedToken)) return cachedToken;
  for (var hi = 0; hi < HOSTS.length; hi++) {
    var url = HOSTS[hi] + '/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=4516404531735022304&page=1&perPage=1';
    for (var ai = 0; ai < 2; ai++) {
      try {
        var res = await fetch(url, { headers: buildHeaders(url, { alt: ai === 1 }) });
        var xUser = res.headers.get('x-user') || '';
        var m = xUser.match(/"token"\s*:\s*"([^"]+)"/);
        var token = m ? m[1] : null;
        if (tokenValid(token)) {
          cachedToken = token;
          return token;
        }
      } catch (e) {}
    }
  }
  return null;
}

// ---- request with host rotation ---------------------------------------------

async function call(path, o) {
  o = o || {};
  var token = tokenValid(o.token || null) ? o.token : await getToken();
  var fallback = null;
  for (var hi = 0; hi < HOSTS.length; hi++) {
    var host = HOSTS[hi];
    var url = host + path;
    for (var ai = 0; ai < 2; ai++) {
      try {
        var opts = Object.assign({}, o, { token: token, alt: ai === 1 });
        var res = await fetch(url, {
          method: o.method || 'GET',
          headers: buildHeaders(url, opts),
          body: o.method === 'POST' ? (o.body || '') : undefined
        });
        var text = (await res.text()).trim();
        if (text.charAt(0) !== '{') continue;
        var json = JSON.parse(text);
        if (!json || json.code !== 0) continue;
        var verdict = o.accept ? o.accept(json) : 'ok';
        if (verdict === 'reject') continue;
        if (verdict === 'fallback') {
          if (!fallback) fallback = { json: json, host: host };
          continue;
        }
        return { json: json, host: host };
      } catch (e) {}
    }
  }
  return fallback;
}

// ---- stream helpers ---------------------------------------------------------

function isBrokenUrl(u) {
  return u.indexOf('b164fbfb43477929') >= 0 || u.indexOf('macdn.aoneroom.com/other/') >= 0;
}

function realStreamUrl(rawUrl, signCookie) {
  var cookie = signCookie ? String(signCookie).trim().replace(/;$/, '') : '';
  if (cookie) {
    if (cookie.indexOf('urlprefix=') >= 0) {
      try {
        var decoded = b64UrlDecode(cookie.split('urlprefix=')[1].split(';')[0].split(':sign=')[0]).trim();
        if (decoded.indexOf('http') === 0) return (decoded.slice(-1) === '/' ? decoded : decoded + '/') + 'index.mpd';
      } catch (e) {}
    }
    if (cookie.indexOf('CloudFront-Policy=') >= 0) {
      try {
        var b64 = cookie.split('CloudFront-Policy=')[1].split(';')[0].replace(/-/g, '+').replace(/_/g, '/').replace(/~/g, '=');
        var policy = JSON.parse(CryptoJS.enc.Base64.parse(b64).toString(CryptoJS.enc.Utf8));
        var resource = policy && policy.Statement && policy.Statement[0] && policy.Statement[0].Resource;
        if (resource && resource.indexOf('http') === 0) {
          var base = resource.slice(0, resource.lastIndexOf('*'));
          return (base.slice(-1) === '/' ? base : base + '/') + 'index.mpd';
        }
      } catch (e) {}
    }
  }
  return rawUrl;
}

function formatOf(url, declared) {
  var d = String(declared || '').toUpperCase();
  if (d === 'DASH') return 'dash';
  if (d === 'HLS') return 'hls';
  if (/\.mpd(\?|$)|\/dash\//.test(url)) return 'dash';
  if (/\.m3u8(\?|$)/.test(url)) return 'hls';
  return 'mp4';
}

function qualityNum(r) {
  return parseInt(String(r).replace(/\D/g, ''), 10) || 0;
}

// ---- search -----------------------------------------------------------------

function norm(s) {
  return s.toLowerCase().replace(/[^a-z0-9]+/g, '');
}
function stripTags(t) {
  return norm(t.replace(/\[[^\]]*\]/g, ' ').replace(/\bS\d+(\s*-\s*S?\d+)?\b/gi, ' '));
}

async function findSubject(title, year, type, season, episode) {
  var body = JSON.stringify({ page: 1, perPage: 20, keyword: title, restrictKid: 1 });
  var r = await call('/wefeed-mobile-bff/subject-api/search/v2', { method: 'POST', body: body });
  var results = (r && r.json && r.json.data && r.json.data.results) || [];
  var subjects = [];
  results.forEach(function (g) {
    (g.subjects || []).forEach(function (s) {
      subjects.push(s);
    });
  });

  if (type === 'tv') {
    var base = norm(title);
    var byId = {};
    var order = [];
    subjects.forEach(function (s) {
      if (s.subjectType !== 2 || stripTags(String(s.title || '')) !== base) return;
      var id = String(s.subjectId);
      var prev = byId[id];
      if (!prev) order.push(id);
      if (!prev || (/\[/.test(String(prev.title)) && !/\[/.test(String(s.title)))) byId[id] = s;
    });
    var ordered = order.map(function (id) {
      return byId[id];
    });
    ordered.sort(function (a, b) {
      return Number(/\[/.test(String(a.title))) - Number(/\[/.test(String(b.title)));
    });
    var checks = await Promise.all(
      ordered.slice(0, 5).map(function (cand) {
        return call('/wefeed-mobile-bff/subject-api/season-info?subjectId=' + cand.subjectId, { details: true }).then(function (si) {
          var seasons = (si && si.json && si.json.data && si.json.data.seasons) || [];
          return seasons.some(function (x) {
            return Number(x.se) === season && Number(x.maxEp) >= episode;
          });
        });
      })
    );
    var hitIdx = checks.indexOf(true);
    if (hitIdx >= 0) return ordered[hitIdx];
  }

  var want = type === 'movie' ? 1 : 2;
  var best = null;
  var bestScore = 0;
  subjects.forEach(function (s) {
    if (s.subjectType !== want) return;
    var t = norm(String(s.title || ''));
    var y = String(s.year || String(s.releaseDate || '').slice(0, 4) || '');
    var score = 0;
    if (t === norm(title)) score += 50;
    else if (t.indexOf(norm(title)) >= 0 || norm(title).indexOf(t) >= 0) score += 15;
    if (year && y && year === y) score += 35;
    if (score > bestScore) {
      bestScore = score;
      best = s;
    }
  });
  return bestScore >= 40 ? best : null;
}

// ---- H5 play endpoint -------------------------------------------------------

function h5Referer(detailPath, id) {
  return H5_BASE + '/movies/' + detailPath + '?id=' + id + '&type=/movie/detail&detailSe=&detailEp=&lang=en';
}

async function h5PlayOnce(subjectId, se, ep, detailPath) {
  var token = await getToken();
  var url =
    H5_BASE + '/wefeed-h5api-bff/subject/play?subjectId=' + subjectId + '&se=' + se + '&ep=' + ep +
    '&detailPath=' + encodeURIComponent(detailPath) + '&streamSignType=1&supportCodecs%5Bhevc%5D=1&supportCodecs%5Bh264%5D=1';
  try {
    var headers = {
      'User-Agent': H5_UA,
      Origin: H5_BASE,
      Referer: h5Referer(detailPath, subjectId),
      Accept: 'application/json',
      'x-client-info': '{"timezone":"Asia/Calcutta"}',
      'x-request-lang': 'en',
      'x-vip-restrict': '0',
      'x-no-high-risk-restrict': '0',
      'x-source': ''
    };
    if (token) headers.Authorization = 'Bearer ' + token;
    var res = await fetch(url, { headers: headers });
    var text = (await res.text()).trim();
    if (text.charAt(0) !== '{') return null;
    var j = JSON.parse(text);
    return (j && j.data) || null;
  } catch (e) {
    return null;
  }
}

async function h5Play(subjectId, se, ep, detailPath) {
  var first = await h5PlayOnce(subjectId, se, ep, detailPath);
  if (first) return first;
  // the phone app waits 400 ms here; the host has no timers, so retry immediately
  return h5PlayOnce(subjectId, se, ep, detailPath);
}

// ---- resourceDetectors ------------------------------------------------------

// bcdn.hakunaymatata.com wants a non-browser UA and no Referer (Referer -> 429, browser UA -> 428).
function detectorStreams(detectors, type, season, episode, language) {
  var out = [];
  if (!Array.isArray(detectors)) return out;
  detectors.forEach(function (det) {
    ((det && det.resolutionList) || []).forEach(function (v) {
      var url = String((v && v.resourceLink) || '');
      if (!url || url.indexOf('magnet:') === 0 || isBrokenUrl(url)) return;
      if (type === 'tv') {
        var vs = v.se != null ? Number(v.se) : null;
        var ve = v.ep != null ? Number(v.ep) : null;
        if (vs !== null && ve !== null && (vs !== season || ve !== episode)) return;
      }
      var n = qualityNum(String(v.resolution || ''));
      out.push({
        url: url,
        format: formatOf(url),
        quality: n ? n + 'p' : 'Auto',
        qualityNum: n,
        language: language,
        headers: { 'User-Agent': APP_UA },
        cdnThrottled: false
      });
    });
  });
  return out;
}

// ---- subtitles (same two calls as v9/v10) -----------------------------------

async function getSubtitles(subjectId, streamId) {
  var rs = await Promise.all([
    call('/wefeed-mobile-bff/subject-api/get-stream-captions?subjectId=' + subjectId + '&streamId=' + streamId).catch(function () {
      return null;
    }),
    call('/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=' + subjectId + '&resourceId=' + streamId + '&episode=0').catch(function () {
      return null;
    })
  ]);
  var out = [];
  var seen = {};
  rs.forEach(function (r) {
    var caps = (r && r.json && r.json.data && r.json.data.extCaptions) || [];
    caps.forEach(function (cap) {
      if (!cap || !cap.url || seen[cap.url]) return;
      seen[cap.url] = true;
      out.push({ url: cap.url, label: cap.lanName || cap.language || cap.lan || 'Subtitle' });
    });
  });
  return out;
}

// ---- TMDB -------------------------------------------------------------------

async function tmdbDetails(type, id) {
  try {
    var res = await fetch('https://api.themoviedb.org/3/' + (type === 'movie' ? 'movie' : 'tv') + '/' + id + '?api_key=' + TMDB_API_KEY);
    return await res.json();
  } catch (e) {
    return null;
  }
}

// ---- main -------------------------------------------------------------------

async function scrape(tmdbId, type, season, episode) {
  var tmdb = await tmdbDetails(type, tmdbId);
  var title = (tmdb && (tmdb.title || tmdb.name)) || '';
  var year = String((tmdb && (tmdb.release_date || tmdb.first_air_date)) || '').slice(0, 4);
  if (!title) return { streams: [], captions: [] };

  var subject = await findSubject(title, year, type, season, episode);
  if (!subject) return { streams: [], captions: [] };
  var subjectId = String(subject.subjectId);

  var detail = await call('/wefeed-mobile-bff/subject-api/get?subjectId=' + subjectId, { details: true });
  var d = detail && detail.json && detail.json.data;
  var originalLang = 'Original';
  var subjects = [];
  var dubs = d && Array.isArray(d.dubs) ? d.dubs : [];
  dubs.forEach(function (dub) {
    if (String(dub.subjectId) === subjectId) originalLang = dub.lanName || 'Original';
    else subjects.push({ id: String(dub.subjectId), lang: dub.lanName, path: dub.detailPath || undefined });
  });
  var detailPath =
    (d && d.subject && d.subject.detailPath) ||
    (d && d.detailPath) ||
    String((d && d.detailUrl) || '').split('/').pop().split('?')[0] ||
    '';
  // H5 play keys on detailPath, so a dub must use its OWN path (the original's returns the original audio)
  subjects.unshift({ id: subjectId, lang: originalLang, path: detailPath });

  var se = type === 'tv' ? season : 0;
  var ep = type === 'tv' ? episode : 0;
  var streams = detectorStreams(d && d.resourceDetectors, type, season, episode, originalLang);
  var captions = [];
  function pushCaps(list) {
    list.forEach(function (cap) {
      var url = cap && (cap.url || cap.file);
      if (url) captions.push({ url: url, label: cap.lanName || cap.language || cap.name || 'Unknown' });
    });
  }

  await Promise.all(
    subjects.map(async function (s) {
      var language = s.lang || 'Original';

      if (!s.path) {
        var dd = await call('/wefeed-mobile-bff/subject-api/get?subjectId=' + s.id, { details: true }).catch(function () {
          return null;
        });
        var ddd = dd && dd.json && dd.json.data;
        s.path =
          (ddd && ddd.subject && ddd.subject.detailPath) ||
          (ddd && ddd.detailPath) ||
          (String((ddd && ddd.detailUrl) || '').split('/').pop().split('?')[0] || undefined);
      }
      var dPath = s.path;
      if (dPath) {
        var h5 = await h5Play(s.id, se, ep, dPath);
        var got = 0;
        ((h5 && h5.streams) || []).forEach(function (st) {
          var url = String(st.url || '');
          if (!url || url.indexOf('magnet:') === 0 || isBrokenUrl(url)) return;
          String(st.resolutions || 'Auto').split(',').forEach(function (r) {
            var n = qualityNum(r);
            streams.push({
              url: url,
              format: formatOf(url, st.format),
              quality: n ? n + 'p' : 'Auto',
              qualityNum: n,
              language: language,
              headers: { Referer: H5_BASE + '/', 'User-Agent': H5_UA },
              cdnThrottled: false,
              subjectId: s.id,
              streamId: String(st.id || '')
            });
            got++;
          });
        });
        ((h5 && h5.dash) || []).forEach(function (dsh) {
          var url = String(dsh.url || '');
          if (!url || isBrokenUrl(url)) return;
          var cookie = dsh.signCookie ? String(dsh.signCookie).trim().replace(/;$/, '') : '';
          var headers = { Origin: H5_BASE, Referer: h5Referer(dPath, s.id), 'User-Agent': H5_UA };
          if (cookie) {
            headers.Cookie = cookie;
            headers[String(dsh.signHeaderKey || 'X-MB-Token')] = cookie;
          }
          String(dsh.resolutions || 'Auto').split(',').forEach(function (r) {
            var n = qualityNum(r);
            streams.push({
              url: url,
              format: 'dash',
              quality: n ? n + 'p' : 'Auto',
              qualityNum: n,
              language: language,
              headers: headers,
              cdnThrottled: false,
              subjectId: s.id,
              streamId: String(dsh.id || '')
            });
            got++;
          });
        });
        pushCaps([].concat((h5 && h5.captions) || [], (h5 && h5.extCaptions) || []));
        if (got > 0) return;
      }

      var r = await call('/wefeed-mobile-bff/subject-api/play-info?subjectId=' + s.id + '&se=' + se + '&ep=' + ep, {
        playback: true,
        accept: function (json) {
          var list = (json && json.data && json.data.streams) || [];
          if (!list.length) return 'reject';
          var allBroken = list.every(function (x) {
            return isBrokenUrl(realStreamUrl(String(x.url || ''), x.signCookie));
          });
          if (allBroken) return 'reject';
          return json.data.cdnThrottleLevel === 1 ? 'fallback' : 'ok';
        }
      });
      var data = r && r.json && r.json.data;
      ((data && data.streams) || []).forEach(function (st) {
        var url = realStreamUrl(String(st.url || ''), st.signCookie);
        if (!url || url.indexOf('magnet:') === 0 || isBrokenUrl(url)) return;
        var cookie = st.signCookie ? String(st.signCookie).trim().replace(/;$/, '') : '';
        var headers = { 'User-Agent': APP_UA, Referer: REFERER };
        if (cookie) headers.Cookie = cookie;
        String(st.resolutions || 'Auto').split(',').forEach(function (q) {
          var n = qualityNum(q);
          streams.push({
            url: url,
            format: formatOf(url, st.format),
            quality: n ? n + 'p' : 'Auto',
            qualityNum: n,
            language: language,
            headers: headers,
            cdnThrottled: data.cdnThrottleLevel === 1,
            subjectId: s.id,
            streamId: String(st.id || '')
          });
        });
      });
      pushCaps([].concat((data && data.captions) || [], (data && data.extCaptions) || []));
    })
  );

  // a dub that resolved to the original's file is not a real language version
  var origUrls = {};
  streams.forEach(function (x) {
    if (x.subjectId === subjectId || !x.subjectId) origUrls[x.url.split('?')[0]] = true;
  });
  streams = streams.filter(function (x) {
    return !(x.subjectId && x.subjectId !== subjectId && origUrls[x.url.split('?')[0]]);
  });
  streams.sort(function (a, b) {
    return Number(a.cdnThrottled) - Number(b.cdnThrottled) || b.qualityNum - a.qualityNum;
  });

  // Per-link subtitles (first few unique links only, to keep the run short)
  var capByUrl = {};
  var seenRef = {};
  var refs = [];
  streams.forEach(function (st) {
    if (!st.subjectId || !st.streamId) return;
    var k = st.subjectId + '|' + st.streamId;
    if (seenRef[k]) return;
    seenRef[k] = true;
    refs.push(st);
  });
  await Promise.all(
    refs.slice(0, 4).map(function (st) {
      return getSubtitles(st.subjectId, st.streamId)
        .then(function (caps) {
          streams.forEach(function (x) {
            if (x.subjectId === st.subjectId && x.streamId === st.streamId) capByUrl[x.url + '|' + x.streamId] = caps;
          });
        })
        .catch(function () {});
    })
  );
  return { streams: streams, captions: captions, capByUrl: capByUrl };
}

async function getStreams(tmdbId, mediaType, season, episode) {
  var type = mediaType === 'movie' ? 'movie' : 'tv';
  var s = Number(season) || 1;
  var e = Number(episode) || 1;
  try {
    var result = await scrape(tmdbId, type, s, e);
    var shared = (result.captions || []).map(function (c) {
      return { url: c.url, language: c.label, name: 'MovieBox' };
    });
    var seenKeys = {};
    return result.streams
      .filter(function (st) {
        var k = st.url + '|' + st.quality + '|' + st.language;
        if (seenKeys[k]) return false;
        seenKeys[k] = true;
        return true;
      })
      .map(function (st) {
        var own = ((result.capByUrl || {})[st.url + '|' + st.streamId] || []).map(function (c) {
          return { url: c.url, language: c.label, name: 'MovieBox' };
        });
        var subs = own.length ? own : shared;
        return {
          name: st.quality + ' · ' + st.format.toUpperCase(),
          title: 'MovieBox · ' + st.quality + ' · ' + st.language,
          url: st.url,
          quality: st.quality,
          language: st.language,
          provider: 'MovieBox',
          type: st.format,
          headers: st.headers,
          subtitles: subs
        };
      });
  } catch (err) {
    console.error('moviebox error: ' + (err && err.message ? err.message : err));
    return [];
  }
}

module.exports = { getStreams: getStreams };

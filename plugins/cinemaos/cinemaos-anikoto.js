/**
 * CinemaOS - Anikoto anime (Nuvio QuickJS plugin).
 * Port of the phone app's vega "anikoto" provider (lib/vega/anikoto + lib/scrapers/anime.ts):
 *  id -> arm.haglund.dev (kitsu/anilist/mal) -> titles (Kitsu REST, AniList GraphQL fallback)
 *  -> anikototv search (/filter?keyword=) -> normalized-title match -> /watch/<slug>/ep-N
 *  -> /ajax/episode/list/<id>?vrf=base64(RC4("simple-hash", id)) -> data-ids -> /ajax/server/list
 *  -> /ajax/server?get= (iframe url + skip data) -> megaplay-style /stream/getSources
 *  -> enc: AES-256-CBC via crypto-js (fallback enc-dec.app) -> master m3u8 + VTT tracks.
 * Accepts kitsu:/anilist:/mal: ids (absolute episode, season null) and numeric TMDB ids (via arm).
 * Host notes: no timers/TextDecoder/cheerio, so HTML is parsed with regexes; Content-Type capitalised on POST.
 */
var CryptoJS = require('crypto-js');

var BASES = ['https://anikototv.bz', 'https://anikototv.to'];
var ARM = 'https://arm.haglund.dev/api/v2';
var DECRYPT_URL = 'https://enc-dec.app/api/dec-megaplay';
var UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36';
var AES_KEY = 'i?LMTAx0Q6,:}50U'; // 16 chars, zero-padded to 32 bytes (as in the vega bundle)
var AES_IV = "W0;27ToaUpl_P%'c";
var MAX_SERVERS = 8;

// ---- helpers ------------------------------------------------------------------

function rc4(key, input) {
  var s = [], a = 0, n, tmp;
  for (n = 0; n < 256; n++) s[n] = n;
  for (n = 0; n < 256; n++) { a = (s[n] + a + key.charCodeAt(n % key.length)) % 256; tmp = s[n]; s[n] = s[a]; s[a] = tmp; }
  var out = '', n2 = 0, a2 = 0;
  for (var r = 0; r < input.length; r++) {
    n2 = (n2 + 1) % 256; a2 = (s[n2] + a2) % 256;
    tmp = s[n2]; s[n2] = s[a2]; s[a2] = tmp;
    out += String.fromCharCode(input.charCodeAt(r) ^ s[(s[n2] + s[a2]) % 256]);
  }
  return out;
}
function encodeVrf(id) {
  var bin = rc4('simple-hash', String(id));
  var bytes = [];
  for (var i = 0; i < bin.length; i++) bytes.push(('0' + bin.charCodeAt(i).toString(16)).slice(-2));
  return CryptoJS.enc.Base64.stringify(CryptoJS.enc.Hex.parse(bytes.join('')));
}
function b64ToUtf8(b64) {
  try { return CryptoJS.enc.Base64.parse(b64).toString(CryptoJS.enc.Utf8); } catch (e) { return ''; }
}
function decryptEnc(enc) {
  try {
    var b64 = String(enc).replace(/-/g, '+').replace(/_/g, '/');
    while (b64.length % 4) b64 += '=';
    var keyHex = '';
    for (var i = 0; i < 32; i++) keyHex += ('0' + (i < AES_KEY.length ? AES_KEY.charCodeAt(i) : 0).toString(16)).slice(-2);
    var dec = CryptoJS.AES.decrypt({ ciphertext: CryptoJS.enc.Base64.parse(b64) }, CryptoJS.enc.Hex.parse(keyHex), {
      iv: CryptoJS.enc.Utf8.parse(AES_IV), mode: CryptoJS.mode.CBC, padding: CryptoJS.pad.Pkcs7
    });
    return dec.toString(CryptoJS.enc.Utf8) || '';
  } catch (e) { return ''; }
}
function norm(s) {
  return String(s || '').toLowerCase().replace(/&/g, 'and')
    .replace(/\b(season|part|cour)\s*\d+\b|\b\d+(st|nd|rd|th)\s+season\b/g, '')
    .replace(/[^a-z0-9]+/g, '');
}
function seasonOf(t) {
  var m = String(t || '').match(/season\s*(\d+)|(\d+)(?:st|nd|rd|th)\s+season|\bpart\s*(\d+)/i);
  return m ? Number(m[1] || m[2] || m[3]) || 1 : 1;
}
function attr(tag, name) {
  var m = tag.match(new RegExp('\\b' + name + '="([^"]*)"'));
  return m ? m[1] : '';
}
function decodeEntities(s) {
  return String(s).replace(/&amp;/g, '&').replace(/&#0?39;|&apos;/g, "'").replace(/&quot;/g, '"').replace(/&lt;/g, '<').replace(/&gt;/g, '>');
}
function inferLang(label) {
  var l = String(label || '').toLowerCase();
  var map = [['english', 'en'], ['spanish', 'es'], ['french', 'fr'], ['german', 'de'], ['portuguese', 'pt'], ['italian', 'it'], ['japanese', 'ja'], ['chinese', 'zh'], ['indonesian', 'id'], ['thai', 'th'], ['vietnamese', 'vi'], ['arabic', 'ar'], ['hindi', 'hi'], ['korean', 'ko'], ['russian', 'ru']];
  for (var i = 0; i < map.length; i++) if (l.indexOf(map[i][0]) !== -1) return map[i][1];
  return 'und';
}
async function getJson(url, headers) {
  var res = await fetch(url, { headers: headers || { Accept: 'application/json' } });
  if (!res.ok) throw new Error('HTTP ' + res.status);
  return await res.json();
}

// ---- id + title resolution ------------------------------------------------------

/** -> [{ mal, anilist, kitsu, episode }] candidates, best first. */
async function resolveTargets(tmdbId, mediaType, season, episode) {
  var id = String(tmdbId || '').trim();
  var ep = Number(episode) || 1;
  var m = id.match(/^(kitsu|anilist|mal|myanimelist):(\d+)/i);
  if (m) {
    var src = m[1].toLowerCase() === 'mal' ? 'myanimelist' : m[1].toLowerCase();
    var r = await getJson(ARM + '/ids?source=' + src + '&id=' + m[2]);
    r = r || {};
    // keep the given id even when arm lacks the other mappings
    var t = { mal: r.myanimelist, anilist: r.anilist, kitsu: r.kitsu, episode: ep };
    if (src === 'kitsu' && !t.kitsu) t.kitsu = Number(m[2]);
    if (src === 'anilist' && !t.anilist) t.anilist = Number(m[2]);
    if (src === 'myanimelist' && !t.mal) t.mal = Number(m[2]);
    return [t];
  }
  if (/^\d+$/.test(id)) {
    var list;
    try { list = await getJson(ARM + '/themoviedb?id=' + id); } catch (e) { return []; }
    if (!Array.isArray(list)) return [];
    var out = [];
    var s = Number(season) || 1;
    list.forEach(function (e) {
      if (!e || !(e.kitsu || e.anilist || e.myanimelist)) return;
      if (mediaType === 'movie') {
        if (e.media === 'MOVIE') out.push({ mal: e.myanimelist, anilist: e.anilist, kitsu: e.kitsu, episode: 1 });
      } else if ((e.media === 'TV' || e.media === 'ONA') && e['themoviedb-season'] === s) {
        out.push({ mal: e.myanimelist, anilist: e.anilist, kitsu: e.kitsu, episode: ep });
      }
    });
    return out;
  }
  return [];
}

async function titlesFor(t) {
  var titles = [];
  function add(x) { if (x && titles.indexOf(x) === -1) titles.push(x); }
  if (t.kitsu) {
    try {
      var k = await getJson('https://kitsu.io/api/edge/anime/' + t.kitsu, { Accept: 'application/vnd.api+json' });
      var a = (k && k.data && k.data.attributes) || {};
      add(a.titles && a.titles.en); add(a.canonicalTitle); add(a.titles && a.titles.en_jp);
      (a.abbreviatedTitles || []).slice(0, 1).forEach(add);
    } catch (e) {}
  }
  if (titles.length === 0 && (t.anilist || t.mal)) {
    try {
      var q = 'query($id:Int){Media(' + (t.anilist ? 'id:$id' : 'idMal:$id') + ',type:ANIME){title{english romaji native}synonyms}}';
      var res = await fetch('https://graphql.anilist.co', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ query: q, variables: { id: Number(t.anilist || t.mal) } })
      });
      if (res.ok) {
        var j = await res.json();
        var ti = j && j.data && j.data.Media && j.data.Media.title;
        if (ti) { add(ti.english); add(ti.romaji); }
      }
    } catch (e) {}
  }
  return titles;
}

// ---- anikoto site ---------------------------------------------------------------

function siteHeaders(base, referer, ajax) {
  var h = { 'User-Agent': UA, Accept: '*/*', Referer: referer || base + '/' };
  if (ajax) h['X-Requested-With'] = 'XMLHttpRequest';
  return h;
}

/** -> [{ title, slug, base }] from the search results page. */
async function search(query) {
  for (var b = 0; b < BASES.length; b++) {
    try {
      var base = BASES[b];
      var res = await fetch(base + '/filter?keyword=' + encodeURIComponent(query) + '&page=1', { headers: siteHeaders(base) });
      if (!res.ok) continue;
      var html = await res.text();
      var out = [];
      var re = /<a\s[^>]*class="name[^"]*"[^>]*>/g, mm;
      while ((mm = re.exec(html))) {
        var href = attr(mm[0], 'href');
        var sm = href.match(/\/watch\/([^/?#]+)/);
        var jp = decodeEntities(attr(mm[0], 'data-jp'));
        if (!sm || !jp) continue;
        out.push({ title: jp, slug: sm[1], base: base });
      }
      if (out.length) return out;
    } catch (e) {}
  }
  return [];
}

/** Ranked list of search hits for the title set. */
async function findCandidates(titles, wantSeason) {
  var seen = {}, cands = [];
  for (var i = 0; i < titles.length && cands.length < 3; i++) {
    var q = titles[i].replace(/\s*(season\s*\d+|\d+(st|nd|rd|th)\s+season|part\s*\d+)\s*$/i, '').trim();
    var hits = await search(titles[i]);
    if (!hits.length && q !== titles[i]) hits = await search(q);
    var exact = [], loose = [];
    hits.forEach(function (h) {
      var nh = norm(h.title);
      if (nh === norm(titles[i]) || nh === norm(q)) exact.push(h);
      else if (nh.indexOf(norm(q)) === 0 && seasonOf(h.title) === wantSeason) loose.push(h);
    });
    exact.concat(loose).forEach(function (h) {
      if (!seen[h.slug]) { seen[h.slug] = 1; cands.push(h); }
    });
  }
  return cands;
}

/** -> { dataIds, mal } for the requested episode of a series slug, or null. */
async function episodeInfo(cand, epNum) {
  var watchUrl = cand.base + '/watch/' + cand.slug + '/ep-' + epNum;
  var res = await fetch(watchUrl, { headers: siteHeaders(cand.base) });
  if (!res.ok) return null;
  var html = await res.text();
  var idm = html.match(/<[^>]*\bid="watch-main"[^>]*\bdata-id="(\d+)"/) || html.match(/\bdata-id="(\d+)"/);
  if (!idm) return null;
  var vrf = encodeURIComponent(encodeVrf(idm[1]));
  var er = await fetch(cand.base + '/ajax/episode/list/' + idm[1] + '?vrf=' + vrf + '&style=default', { headers: siteHeaders(cand.base, watchUrl, true) });
  if (!er.ok) return null;
  var ej = await er.json();
  if (!ej || ej.status !== 200 || !ej.result) return null;
  var re = /<a\s[^>]*data-num="([^"]*)"[^>]*>/g, mm;
  while ((mm = re.exec(ej.result))) {
    if (String(Number(attr(mm[0], 'data-num'))) === String(Number(epNum)) || attr(mm[0], 'data-num') === String(epNum)) {
      var ids = attr(mm[0], 'data-ids');
      if (ids) return { dataIds: ids, mal: attr(mm[0], 'data-mal'), watchUrl: watchUrl };
    }
  }
  return null;
}

/** -> [{ type, name, linkId }] */
async function listServers(cand, info) {
  var res = await fetch(cand.base + '/ajax/server/list?servers=' + encodeURIComponent(info.dataIds), { headers: siteHeaders(cand.base, info.watchUrl, true) });
  if (!res.ok) return [];
  var j = await res.json();
  if (!j || j.status !== 200 || !j.result) return [];
  var out = [], seen = {};
  var parts = String(j.result).split(/<div class="type"/).slice(1);
  parts.forEach(function (part) {
    var type = (part.match(/data-type="([^"]*)"/) || [])[1] || 'sub';
    var re = /<li\s[^>]*data-link-id="([^"]+)"[^>]*>([\s\S]*?)<\/li>/g, mm;
    while ((mm = re.exec(part))) {
      if (seen[mm[1]]) continue;
      seen[mm[1]] = 1;
      out.push({ type: type, linkId: mm[1], name: decodeEntities(mm[2].replace(/<[^>]*>/g, '')).trim() || 'Server' });
    }
  });
  return out;
}

async function decodeSource(data) {
  var cand = '';
  function pick(p) {
    if (typeof p === 'string') return /^https?:/.test(p) ? p : '';
    if (!p) return '';
    if (typeof p.file === 'string' && /^https?:/.test(p.file)) return p.file;
    if (typeof p.sources === 'string' && /^https?:/.test(p.sources)) return p.sources;
    if (p.sources && typeof p.sources.file === 'string') return p.sources.file;
    if (Array.isArray(p.sources) && p.sources[0]) return typeof p.sources[0] === 'string' ? p.sources[0] : (p.sources[0].file || '');
    return '';
  }
  if (data.enc && typeof data.enc === 'string') {
    var txt = decryptEnc(data.enc);
    if (txt) {
      try { cand = pick(JSON.parse(txt)); } catch (e) { cand = /^https?:/.test(txt) ? txt : ''; }
    }
    if (!cand) {
      try {
        var dr = await fetch(DECRYPT_URL, { method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' }, body: JSON.stringify({ text: data.enc }) });
        if (dr.ok) { var dj = await dr.json(); if (dj && dj.status === 200) cand = pick(dj.result); }
      } catch (e) {}
    }
  }
  return cand || pick(data);
}

/** Resolve one server entry to a stream object, or null. */
async function resolveServer(cand, info, srv) {
  try {
    var gr = await fetch(cand.base + '/ajax/server?get=' + encodeURIComponent(srv.linkId), { headers: siteHeaders(cand.base, info.watchUrl, true) });
    if (!gr.ok) return null;
    var gj = await gr.json();
    var iframe = gj && gj.result && gj.result.url;
    if (!iframe) return null;
    var host = (iframe.match(/^https?:\/\/([^/?#]+)/) || [])[1] || '';
    var audio = srv.type.toUpperCase();
    // vibeplayer-style: base64 m3u8 in the URL fragment
    if (iframe.indexOf('#') !== -1) {
      var frag = b64ToUtf8(iframe.substring(iframe.indexOf('#') + 1));
      if (/^https?:/.test(frag)) {
        var h2 = { Referer: 'https://vibeplayer.site/', Origin: 'https://vibeplayer.site', 'User-Agent': UA };
        return { url: frag, headers: h2, subtitles: [], audio: audio, server: srv.name, skip: gj.result.skip_data };
      }
    }
    var pr = await fetch(iframe, { headers: { 'User-Agent': UA, Accept: '*/*', Referer: 'https://' + host + '/', Origin: 'https://' + host } });
    if (!pr.ok) return null;
    var page = await pr.text();
    var m1 = page.match(/data-id=["']?([^"'\s>]+)/i);
    var m2 = page.match(/data-realid=["']?([^"'\s>]+)/i);
    var um = iframe.match(/\/stream\/[^/]+\/([^/?#]+)/);
    var ids = [m1 ? m1[1] : um ? um[1] : '', m2 ? m2[1] : ''].filter(function (v, i, a) { return v && a.indexOf(v) === i; });
    var typeParam = /\/hsub/.test(iframe) ? 'hsub' : /\/hdub/.test(iframe) ? 'hdub' : srv.type;
    var types = [typeParam, srv.type].filter(function (v, i, a) { return v && a.indexOf(v) === i; });
    var sm = iframe.match(/[?&]s=([^&]+)/);
    var sList = sm ? [decodeURIComponent(sm[1]), ''] : [''];
    var apiHeaders = { 'User-Agent': UA, Accept: '*/*', 'X-Requested-With': 'XMLHttpRequest', Referer: iframe, Origin: 'https://' + host };
    for (var a = 0; a < ids.length; a++) for (var b = 0; b < types.length; b++) for (var c = 0; c < sList.length; c++) {
      var url = 'https://' + host + '/stream/getSources?id=' + ids[a] + '&type=' + types[b] + (sList[c] ? '&s=' + encodeURIComponent(sList[c]) : '');
      try {
        var sr = await fetch(url, { headers: apiHeaders });
        if (!sr.ok) continue;
        var data = await sr.json();
        var master = await decodeSource(data);
        if (!master) continue;
        var sh = { Referer: 'https://' + host + '/', Origin: 'https://' + host, 'User-Agent': UA };
        var subs = [];
        (data.tracks || []).forEach(function (tr) {
          if (tr && typeof tr.file === 'string' && /^https?:/.test(tr.file) && tr.label && tr.kind !== 'thumbnails') {
            subs.push({ id: 'anikoto-' + srv.linkId.slice(-6) + '-' + subs.length, url: tr.file, lang: inferLang(tr.label), label: String(tr.label), headers: sh });
          }
        });
        var skip = data.intro || data.outro ? { intro: data.intro, outro: data.outro } : gj.result.skip_data;
        return { url: master, headers: sh, subtitles: subs, audio: audio, server: srv.name, skip: skip };
      } catch (e) {}
    }
  } catch (e) {}
  return null;
}

async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    var targets = await resolveTargets(tmdbId, mediaType, season, episode);
    for (var ti = 0; ti < targets.length; ti++) {
      var t = targets[ti];
      var titles = await titlesFor(t);
      if (!titles.length) continue;
      var wantSeason = Math.max.apply(null, titles.map(seasonOf));
      var cands = await findCandidates(titles, wantSeason);
      var chosen = null, info = null, fallback = null;
      for (var ci = 0; ci < cands.length; ci++) {
        var i2 = await episodeInfo(cands[ci], t.episode);
        if (!i2) continue;
        if (!fallback) fallback = { c: cands[ci], i: i2 };
        // prefer the entry whose episode rows carry our MAL id
        if (!t.mal || !i2.mal || String(i2.mal) === String(t.mal)) { chosen = cands[ci]; info = i2; break; }
      }
      if (!chosen && fallback) { chosen = fallback.c; info = fallback.i; }
      if (!chosen) continue;
      var servers = (await listServers(chosen, info)).slice(0, MAX_SERVERS);
      var resolved = await Promise.all(servers.map(function (s) { return resolveServer(chosen, info, s); }));
      var out = [], seen = {};
      resolved.forEach(function (r) {
        if (!r || seen[r.url]) return;
        seen[r.url] = 1;
        var label = r.audio === 'SUB' ? 'SUB' : r.audio === 'DUB' ? 'DUB' : r.audio === 'HSUB' ? 'HARDSUB' : r.audio === 'HDUB' ? 'HARDDUB' : r.audio;
        out.push({
          name: 'Anikoto · ' + label,
          title: 'Anikoto · ' + r.server + ' · ' + label,
          url: r.url,
          quality: 'Auto',
          language: /DUB/.test(r.audio) ? 'English' : 'Japanese',
          provider: 'Anikoto',
          type: 'hls',
          headers: r.headers,
          subtitles: r.subtitles
        });
      });
      if (out.length) return out;
    }
    return [];
  } catch (e) {
    console.log('[cinemaos-anikoto] ' + (e && e.message ? e.message : e));
    return [];
  }
}

module.exports = { getStreams: getStreams };

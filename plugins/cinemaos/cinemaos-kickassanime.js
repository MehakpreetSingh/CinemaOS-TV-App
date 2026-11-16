/**
 * CinemaOS - KickAssAnime (kaa.lt) anime (Nuvio QuickJS plugin).
 * Port of the phone app's vega "kickAssAnime" provider bundle (lib/vega/kickAssAnime) driven like lib/scrapers/anime.ts:
 *  id (kitsu:/anilist:/mal:/TMDB via arm.haglund.dev) -> titles -> provider search (exact / prefix title match)
 *  -> meta season list -> episode link -> stream resolution. The original vega bundles are embedded unchanged
 *  behind a small axios/cheerio/providerContext adapter (host has no axios; cheerio is the host's jsoup shim).
 * Anime ids only: ids that cannot be mapped to an anime entry return []. Host notes: no timers, no TextDecoder.
 */
var ARM = 'https://arm.haglund.dev/api/v2';
var cheerio = require('cheerio');
if (typeof TextDecoder === 'undefined') {
  globalThis.TextDecoder = function () {};
  globalThis.TextDecoder.prototype.decode = function (b) {
    var s = ''; for (var i = 0; i < b.length; i++) s += String.fromCharCode(b[i]);
    try { return decodeURIComponent(escape(s)); } catch (e) { return s; }
  };
}

// ---- axios adapter over the host fetch ---------------------------------------------
async function __axiosReq(method, url, data, cfg) {
  cfg = cfg || {};
  var headers = {}; var h0 = cfg.headers || {};
  Object.keys(h0).forEach(function (k) { headers[k] = h0[k]; });
  if (cfg.params) {
    var qs = Object.keys(cfg.params).map(function (k) { return encodeURIComponent(k) + '=' + encodeURIComponent(cfg.params[k]); }).join('&');
    url += (url.indexOf('?') === -1 ? '?' : '&') + qs;
  }
  var body;
  if (method !== 'GET' && data != null) {
    if (typeof data === 'string') body = data;
    else { body = JSON.stringify(data); if (!headers['Content-Type'] && !headers['content-type']) headers['Content-Type'] = 'application/json'; }
  }
  var res = await fetch(url, { method: method, headers: headers, body: body });
  var text = await res.text();
  if (!res.ok) { var err = new Error('Request failed with status code ' + res.status); err.response = { status: res.status, data: text }; throw err; }
  var out = text, t = String(text).trim();
  if (t.charAt(0) === '{' || t.charAt(0) === '[') { try { out = JSON.parse(t); } catch (e) {} }
  return { data: out, status: res.status, headers: {} };
}
var axios = {
  get: function (url, cfg) { return __axiosReq('GET', url, null, cfg); },
  post: function (url, data, cfg) { return __axiosReq('POST', url, data, cfg); }
};
var __kv = {};
var providerContext = {
  axios: axios,
  cheerio: cheerio,
  commonHeaders: { 'User-Agent': 'Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36' },
  kvStore: {
    get: async function (k) { return __kv[k] == null ? null : __kv[k]; },
    set: async function (k, v) { __kv[k] = v; },
    delete: async function (k) { delete __kv[k]; }
  },
  Aes: {}
};
function __mod(fn) { var m = { exports: {} }; fn(m.exports, m); return m.exports; }

// ---- embedded vega bundles (unchanged) ------------------------------------------------
var postsMod = __mod(function (exports, module) {
"use strict";var __defProp=Object.defineProperty,__defProps=Object.defineProperties,__getOwnPropDesc=Object.getOwnPropertyDescriptor,__getOwnPropDescs=Object.getOwnPropertyDescriptors,__getOwnPropNames=Object.getOwnPropertyNames,__getOwnPropSymbols=Object.getOwnPropertySymbols,__hasOwnProp=Object.prototype.hasOwnProperty,__propIsEnum=Object.prototype.propertyIsEnumerable,__defNormalProp=(obj,key,value)=>key in obj?__defProp(obj,key,{enumerable:!0,configurable:!0,writable:!0,value:value}):obj[key]=value,__spreadValues=(a,b)=>{for(var prop in b||(b={}))__hasOwnProp.call(b,prop)&&__defNormalProp(a,prop,b[prop]);if(__getOwnPropSymbols)for(var prop of __getOwnPropSymbols(b))__propIsEnum.call(b,prop)&&__defNormalProp(a,prop,b[prop]);return a},__spreadProps=(a,b)=>__defProps(a,__getOwnPropDescs(b)),__name=(target,value)=>__defProp(target,"name",{value:value,configurable:!0}),__export=(target,all)=>{for(var name in all)__defProp(target,name,{get:all[name],enumerable:!0})},__copyProps=(to,from,except,desc)=>{if(from&&"object"==typeof from||"function"==typeof from)for(let key of __getOwnPropNames(from))__hasOwnProp.call(to,key)||key===except||__defProp(to,key,{get:()=>from[key],enumerable:!(desc=__getOwnPropDesc(from,key))||desc.enumerable});return to},__toCommonJS=mod=>__copyProps(__defProp({},"__esModule",{value:!0}),mod),__async=(__this,__arguments,generator)=>new Promise((resolve,reject)=>{var fulfilled=value=>{try{step(generator.next(value))}catch(e){reject(e)}},rejected=value=>{try{step(generator.throw(value))}catch(e){reject(e)}},step=x=>x.done?resolve(x.value):Promise.resolve(x.value).then(fulfilled,rejected);step((generator=generator.apply(__this,__arguments)).next())}),posts_exports={};__export(posts_exports,{getPosts:()=>getPosts,getSearchPosts:()=>getSearchPosts});var BASE_URL="https://kaa.lt",headers={"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",Accept:"application/json, text/plain, */*"};function formatPoster(poster){return poster?poster.hq?`${BASE_URL}/image/poster/${poster.hq}.webp`:poster.sm?`${BASE_URL}/image/poster/${poster.sm}.webp`:poster.url?poster.url.startsWith("http")?poster.url:`${BASE_URL}/${poster.url}`:"":""}__name(formatPoster,"formatPoster");var getPosts=__name(function(_0){return __async(this,arguments,function*({filter:filter,page:page=1,providerContext:providerContext}){var _a;const{axios:axios}=providerContext;let url=`${BASE_URL}${filter}`;if(filter.includes("/api/show/trending"))url=`${BASE_URL}/api/show/trending?page=${page}`;else if(filter.includes("/api/show/popular"))url=`${BASE_URL}/api/show/popular?page=${page}`;else if(filter.includes("/api/show/recent")){const typeMatch=filter.match(/type=([a-z]+)/),type=typeMatch?typeMatch[1]:"all";url=`${BASE_URL}/api/show/recent?type=${type}&page=${page}`}else if(filter.startsWith("/api/anime"))url=`${BASE_URL}/api/anime?page=${page}`;else{const encoded=btoa(JSON.stringify({genres:[filter]}));url=`${BASE_URL}/api/anime?page=${page}&filters=${encoded}`}return((null==(_a=(yield axios.get(url,{headers:headers})).data)?void 0:_a.result)||[]).map(item=>({title:item.title_en||item.title||"",link:`${BASE_URL}/${item.slug}`,image:formatPoster(item.poster)}))})},"getPosts"),getSearchPosts=__name(function(_0){return __async(this,arguments,function*({searchQuery:searchQuery,page:page=1,providerContext:providerContext}){var _a;const{axios:axios}=providerContext;return((null==(_a=(yield axios.post(`${BASE_URL}/api/fsearch`,{page:page,query:searchQuery},{headers:__spreadProps(__spreadValues({},headers),{"Content-Type":"application/json",Referer:`${BASE_URL}/search?q=${encodeURIComponent(searchQuery)}`})})).data)?void 0:_a.result)||[]).map(item=>({title:item.title_en||item.title||"",link:`${BASE_URL}/${item.slug}`,image:formatPoster(item.poster)}))})},"getSearchPosts");exports.getPosts=getPosts,exports.getSearchPosts=getSearchPosts;
});
var metaMod = __mod(function (exports, module) {
"use strict";var __defProp=Object.defineProperty,__getOwnPropDesc=Object.getOwnPropertyDescriptor,__getOwnPropNames=Object.getOwnPropertyNames,__hasOwnProp=Object.prototype.hasOwnProperty,__name=(target,value)=>__defProp(target,"name",{value:value,configurable:!0}),__export=(target,all)=>{for(var name in all)__defProp(target,name,{get:all[name],enumerable:!0})},__copyProps=(to,from,except,desc)=>{if(from&&"object"==typeof from||"function"==typeof from)for(let key of __getOwnPropNames(from))__hasOwnProp.call(to,key)||key===except||__defProp(to,key,{get:()=>from[key],enumerable:!(desc=__getOwnPropDesc(from,key))||desc.enumerable});return to},__toCommonJS=mod=>__copyProps(__defProp({},"__esModule",{value:!0}),mod),__async=(__this,__arguments,generator)=>new Promise((resolve,reject)=>{var fulfilled=value=>{try{step(generator.next(value))}catch(e){reject(e)}},rejected=value=>{try{step(generator.throw(value))}catch(e){reject(e)}},step=x=>x.done?resolve(x.value):Promise.resolve(x.value).then(fulfilled,rejected);step((generator=generator.apply(__this,__arguments)).next())}),meta_exports={};__export(meta_exports,{getMeta:()=>getMeta});var BASE_URL="https://kaa.lt",headers={"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",Accept:"application/json, text/plain, */*"};function formatPoster(poster){return poster?poster.hq?`${BASE_URL}/image/poster/${poster.hq}.webp`:poster.sm?`${BASE_URL}/image/poster/${poster.sm}.webp`:poster.url?poster.url.startsWith("http")?poster.url:`${BASE_URL}/${poster.url}`:"":""}function formatThumbnail(thumbnail){if(thumbnail)return thumbnail.hq?`${BASE_URL}/image/thumbnail/${thumbnail.hq}.webp`:thumbnail.sm?`${BASE_URL}/image/thumbnail/${thumbnail.sm}.webp`:thumbnail.url?thumbnail.url.startsWith("http")?thumbnail.url:`${BASE_URL}/${thumbnail.url}`:void 0}function getLangLabel(lang){return"ja-JP"===lang?"Sub":"en-US"===lang?"Dub":"es-419"===lang||"es-ES"===lang?"Spanish":"ko-KR"===lang?"Korean":"zh-CN"===lang?"Chinese":lang}__name(formatPoster,"formatPoster"),__name(formatThumbnail,"formatThumbnail"),__name(getLangLabel,"getLangLabel");var getMeta=__name(function(_0){return __async(this,arguments,function*({link:link,providerContext:providerContext}){var _a,_b,_c,_d;const{axios:axios}=providerContext,slug=link.replace(/^https?:\/\/[^/]+\//,"").replace(/^\//,"").split("/")[0],data=(yield axios.get(`${BASE_URL}/api/show/${slug}`,{headers:headers})).data||{},title=data.title_en||data.title||slug,synopsis=data.synopsis||"",image=formatPoster(data.poster),type="movie"===data.type?"movie":"series";let languages=["ja-JP"];try{const langRes=yield axios.get(`${BASE_URL}/api/show/${slug}/language`,{headers:headers});Array.isArray(null==(_a=langRes.data)?void 0:_a.result)&&langRes.data.result.length>0&&(languages=langRes.data.result)}catch(e){}const linkList=[];for(const lang of languages)try{const epRes=yield axios.get(`${BASE_URL}/api/show/${slug}/episodes?page=1&lang=${lang}`,{headers:headers});let episodes=(null==(_b=epRes.data)?void 0:_b.result)||[];const pages=(null==(_c=epRes.data)?void 0:_c.pages)||[];if(pages.length>1){const remainingPages=yield Promise.all(pages.slice(1).map((_,idx)=>axios.get(`${BASE_URL}/api/show/${slug}/episodes?page=${idx+2}&lang=${lang}`,{headers:headers}).then(r=>{var _a2;return(null==(_a2=r.data)?void 0:_a2.result)||[]}).catch(()=>[])));episodes=episodes.concat(remainingPages.flat())}if(episodes.length>0){if("ja-JP"!==lang)try{const firstEp=episodes[0],checkRes=yield axios.get(`${BASE_URL}/api/show/${slug}/episode/ep-${firstEp.episode_string}-${firstEp.slug}`,{headers:headers});if(!(null==(_d=checkRes.data)?void 0:_d.servers)||0===checkRes.data.servers.length)continue}catch(e){}const directLinks=episodes.map(ep=>({title:`Episode ${ep.episode_string}${ep.title?` - ${ep.title}`:""}`,link:JSON.stringify({slug:slug,epSlug:ep.slug,epNum:ep.episode_string,lang:lang}),type:type,image:formatThumbnail(ep.thumbnail)})),langLabel=getLangLabel(lang);linkList.push({title:languages.length>1?`Episodes (${langLabel})`:"Episodes",directLinks:directLinks})}}catch(e){}return{title:title,synopsis:synopsis,image:image,imdbId:"",type:type,linkList:linkList,webUrl:`${BASE_URL}/${slug}`}})},"getMeta");exports.getMeta=getMeta;
});
var streamMod = __mod(function (exports, module) {
"use strict";var __defProp=Object.defineProperty,__defProps=Object.defineProperties,__getOwnPropDesc=Object.getOwnPropertyDescriptor,__getOwnPropDescs=Object.getOwnPropertyDescriptors,__getOwnPropNames=Object.getOwnPropertyNames,__getOwnPropSymbols=Object.getOwnPropertySymbols,__hasOwnProp=Object.prototype.hasOwnProperty,__propIsEnum=Object.prototype.propertyIsEnumerable,__defNormalProp=(obj,key,value)=>key in obj?__defProp(obj,key,{enumerable:!0,configurable:!0,writable:!0,value:value}):obj[key]=value,__spreadValues=(a,b)=>{for(var prop in b||(b={}))__hasOwnProp.call(b,prop)&&__defNormalProp(a,prop,b[prop]);if(__getOwnPropSymbols)for(var prop of __getOwnPropSymbols(b))__propIsEnum.call(b,prop)&&__defNormalProp(a,prop,b[prop]);return a},__spreadProps=(a,b)=>__defProps(a,__getOwnPropDescs(b)),__name=(target,value)=>__defProp(target,"name",{value:value,configurable:!0}),__export=(target,all)=>{for(var name in all)__defProp(target,name,{get:all[name],enumerable:!0})},__copyProps=(to,from,except,desc)=>{if(from&&"object"==typeof from||"function"==typeof from)for(let key of __getOwnPropNames(from))__hasOwnProp.call(to,key)||key===except||__defProp(to,key,{get:()=>from[key],enumerable:!(desc=__getOwnPropDesc(from,key))||desc.enumerable});return to},__toCommonJS=mod=>__copyProps(__defProp({},"__esModule",{value:!0}),mod),__async=(__this,__arguments,generator)=>new Promise((resolve,reject)=>{var fulfilled=value=>{try{step(generator.next(value))}catch(e){reject(e)}},rejected=value=>{try{step(generator.throw(value))}catch(e){reject(e)}},step=x=>x.done?resolve(x.value):Promise.resolve(x.value).then(fulfilled,rejected);step((generator=generator.apply(__this,__arguments)).next())}),stream_exports={};__export(stream_exports,{getStream:()=>getStream});var BASE_URL="https://kaa.lt",defaultHeaders={"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",Accept:"application/json, text/plain, */*"};function fixUrl(rawUrl,baseUrl){let trimmed=rawUrl.trim();if(trimmed.startsWith("https://")||trimmed.startsWith("http://"))trimmed=trimmed.replace(/^(https?:)\/\/+/,"$1//");else if(trimmed.startsWith("//"))trimmed="https:"+trimmed;else if(trimmed.startsWith("/"))try{trimmed=`${new URL(baseUrl).origin}${trimmed}`}catch(e){trimmed=`${BASE_URL}${trimmed}`}return trimmed}__name(fixUrl,"fixUrl");var getStream=__name(function(_0){return __async(this,arguments,function*({link:link,providerContext:providerContext}){var _a,_b,_c,_d;const{axios:axios}=providerContext;let epApiUrl=link,epParsed=null;if(link.startsWith("{"))try{const parsed=JSON.parse(link);epParsed=parsed;const slug=parsed.slug,epNum=parsed.epNum,epSlug=parsed.epSlug,num=epNum||"1";epApiUrl=epSlug?`${BASE_URL}/api/show/${slug}/episode/ep-${num}-${epSlug}`:`${BASE_URL}/api/show/${slug}/episode/ep-${num}`}catch(e){}let servers=[];try{if(servers=(null==(_a=(yield axios.get(epApiUrl,{headers:defaultHeaders})).data)?void 0:_a.servers)||[],0===servers.length&&(null==epParsed?void 0:epParsed.slug)&&epParsed.lang&&"ja-JP"!==epParsed.lang)try{const num=epParsed.epNum||"1",matching=((null==(_b=(yield axios.get(`${BASE_URL}/api/show/${epParsed.slug}/episodes?page=1&lang=ja-JP`,{headers:defaultHeaders})).data)?void 0:_b.result)||[]).find(e=>String(e.episode_string)===String(num)||String(e.episode_number)===String(num));(null==matching?void 0:matching.slug)&&(servers=(null==(_c=(yield axios.get(`${BASE_URL}/api/show/${epParsed.slug}/episode/ep-${num}-${matching.slug}`,{headers:defaultHeaders})).data)?void 0:_c.servers)||[])}catch(e){}}catch(e){return[]}if(0===servers.length)return[];const streams=[];for(const srv of servers)if(srv.src)try{let src=srv.src;src.includes("/cat-player/vast")&&(src=src.replace("/cat-player/vast","/cat-player/player"));const playerUrl=fixUrl(src,BASE_URL),playerRes=yield axios.get(playerUrl,{headers:__spreadProps(__spreadValues({},defaultHeaders),{Referer:`${BASE_URL}/`})}),cleanHtml=("string"==typeof playerRes.data?playerRes.data:JSON.stringify(playerRes.data)).replace(/&quot;/g,'"'),directManifestMatch=cleanHtml.match(/manifest":\[\d+,"(?:https?:)?(\/\/[^"]+)"]/)||cleanHtml.match(/manifest":\[\d+,"([^"]+)"]/);if(directManifestMatch){const manifestUrl=fixUrl(directManifestMatch[1].split("\\").join(""),playerUrl);let origin="";try{origin=new URL(playerUrl).origin}catch(e){origin=BASE_URL}const hlsHeaders={"User-Agent":defaultHeaders["User-Agent"],Origin:origin,Referer:playerUrl},subtitles=[],trackRegex=/"language":\[\d+,"([^"]+)"][^}]+?"name":\[\d+,"([^"]+)"][^}]+?"src":\[\d+,"([^"]+)"]/g;let tMatch;for(;null!==(tMatch=trackRegex.exec(cleanHtml));){const lang=tMatch[1],subName=tMatch[2],subUrl=fixUrl(tMatch[3].split("\\").join(""),playerUrl),proxyUrl=`https://fetch.1proxy.workers.dev/fetch?url=${encodeURIComponent(subUrl)}&headers=${encodeURIComponent(JSON.stringify(hlsHeaders))}`,isSrt=subUrl.toLowerCase().includes(".srt");subtitles.push({title:`${subName} (${lang})`,language:lang,type:isSrt?"application/x-subrip":"text/vtt",uri:proxyUrl})}if(manifestUrl.includes(".m3u8")){streams.push({server:`${srv.name} (Auto)`,link:manifestUrl,type:"m3u8",quality:"auto",subtitles:subtitles.length>0?subtitles:void 0,headers:hlsHeaders});try{const lines=(yield axios.get(manifestUrl,{headers:hlsHeaders})).data.split("\n"),baseUrl=manifestUrl.substring(0,manifestUrl.lastIndexOf("/")+1);for(let i=0;i<lines.length;i++){const line=lines[i].trim();if(line.startsWith("#EXT-X-STREAM-INF")){const resMatch=line.match(/RESOLUTION=\d+x(\d+)/),quality=resMatch?`${resMatch[1]}p`:"unknown",nextLine=null==(_d=lines[i+1])?void 0:_d.trim();if(nextLine&&!nextLine.startsWith("#")){const streamUrl=nextLine.startsWith("http")?nextLine:baseUrl+nextLine;streams.push({server:`${srv.name} (${quality})`,link:streamUrl,type:"m3u8",quality:quality,subtitles:subtitles.length>0?subtitles:void 0,headers:hlsHeaders})}}}}catch(e){}}else(manifestUrl.includes(".mpd")||playerUrl.includes("type=dash"))&&streams.push({server:`${srv.name} (DASH)`,link:manifestUrl,type:"dash",quality:"auto",subtitles:subtitles.length>0?subtitles:void 0,headers:hlsHeaders});continue}const sourcesMatch=cleanHtml.match(/sources:\s*(\[[^\]]+\])/);if(sourcesMatch)try{const sources=JSON.parse(sourcesMatch[1]);for(const s of sources)if(s.file){const streamUrl=fixUrl(s.file,playerUrl);streams.push({server:`${srv.name} (${s.label||"default"})`,link:streamUrl,type:streamUrl.includes(".m3u8")?"m3u8":"mp4",quality:s.label||"unknown"})}}catch(e){}}catch(e){}return streams})},"getStream");exports.getStream=getStream;
});

// ---- id + title resolution (arm / Kitsu / AniList) --------------------------------------------
var UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36';
async function getJson(url, headers) {
  var res = await fetch(url, { headers: headers || { Accept: 'application/json' } });
  if (!res.ok) throw new Error('HTTP ' + res.status + ' ' + url);
  return await res.json();
}

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


// ---- driver (RN lib/scrapers/anime.ts fromProvider) ----------------------------------------------
var PROVIDER_KEY = 'kickAssAnime', LABEL = 'KickAssAnime';
function norm2(s) {
  return String(s || '').toLowerCase().replace(/&/g, 'and')
    .replace(/\b(season|part|cour)\s*\d+\b|\b\d+(st|nd|rd|th)\s+season\b/g, '').replace(/[^a-z0-9]+/g, '');
}
function seasonFromTitle(t) {
  var m = String(t).match(/season\s*(\d+)|(\d+)(?:st|nd|rd|th)\s+season|\bpart\s*(\d+)/i);
  if (m) return Number(m[1] || m[2] || m[3]) || 1;
  var roman = String(t).match(/\s(II|III|IV|V)$/);
  return roman ? ['', 'I', 'II', 'III', 'IV', 'V'].indexOf(roman[1]) : 1;
}
function audioOf(server) {
  if (/multi/i.test(server)) return 'Multi-audio';
  if (/hindi/i.test(server)) return 'Hindi';
  if (/\bdub\b/i.test(server)) return 'Dub';
  if (/\bsub\b|softsub|hardsub/i.test(server)) return 'Sub';
  return 'Original';
}
function qualityLabel(q) { var n = String(q == null ? '' : q).match(/(\d{3,4})p?\b/); return n ? n[1] + 'p' : 'Auto'; }
function inferLang(label) {
  var l = String(label || '').toLowerCase();
  var map = [['english', 'en'], ['spanish', 'es'], ['french', 'fr'], ['german', 'de'], ['portuguese', 'pt'], ['italian', 'it'], ['japanese', 'ja'], ['chinese', 'zh'], ['indonesian', 'id'], ['thai', 'th'], ['vietnamese', 'vi'], ['arabic', 'ar'], ['hindi', 'hi'], ['korean', 'ko'], ['russian', 'ru']];
  for (var i = 0; i < map.length; i++) if (l.indexOf(map[i][0]) !== -1) return map[i][1];
  return 'und';
}
/** vega wraps subtitle urls as https://fetch.1proxy.workers.dev/fetch?url=<target>&headers=<json> */
function unwrapSubtitle(raw, streamHeaders, idx) {
  var src = raw && (raw.uri || raw.url || raw.file);
  if (!src) return null;
  var headers = streamHeaders || {};
  try {
    var m = String(src).match(/^https?:\/\/[^/]*1proxy\.workers\.dev\/[^?]*\?(.*)$/);
    if (m) {
      var q = {}; m[1].split('&').forEach(function (p) { var i = p.indexOf('='); if (i > 0) q[p.slice(0, i)] = decodeURIComponent(p.slice(i + 1)); });
      if (q.url) { src = q.url; if (q.headers) headers = JSON.parse(q.headers); }
    }
  } catch (e) {}
  var label = String(raw.title || raw.label || raw.language || 'Subtitle').replace(/\s*\([a-z]{2,3}\)\s*$/i, '').trim();
  var lang = String(raw.language || raw.lang || '').slice(0, 2).toLowerCase() || inferLang(label);
  return { id: PROVIDER_KEY + '-sub-' + idx, url: src, lang: lang, label: label, headers: headers };
}
async function fromProvider(titles, season, episode) {
  var post = null;
  for (var ti = 0; ti < titles.length && !post; ti++) {
    var q = titles[ti].replace(/\s*(season\s*\d+|\d+(st|nd|rd|th)\s+season|part\s*\d+)\s*$/i, '').trim();
    var posts = [];
    try { posts = (await postsMod.getSearchPosts({ searchQuery: q, page: 1, providerValue: PROVIDER_KEY, signal: undefined, providerContext: providerContext })) || []; } catch (e) {}
    post = posts.find(function (p) { return norm2(p.title) === norm2(q); }) || posts.find(function (p) { return norm2(p.title).indexOf(norm2(q)) === 0; }) || null;
  }
  if (!post) return [];
  var meta = await metaMod.getMeta({ link: post.link, provider: PROVIDER_KEY, providerContext: providerContext });
  var lists = (meta && meta.linkList) || [];
  var list;
  if (lists.length > 1) {
    list = lists.find(function (l) { return new RegExp('season\\s*' + season + '\\b', 'i').test(l.title); }) || lists[season - 1] || lists[0];
  } else list = lists[0];
  if (!list) return [];
  var link;
  if (list.directLinks && list.directLinks.length) {
    var eps = list.directLinks;
    var hit = eps.find(function (e) { return Number((String(e.title).match(/(\d+)/) || [])[1]) === episode; }) || eps[episode - 1];
    link = hit && hit.link;
  } else if (list.episodesLink && typeof episodesMod !== 'undefined') {
    var eps2 = (await episodesMod.getEpisodes({ url: list.episodesLink, providerContext: providerContext })) || [];
    var hit2 = eps2.find(function (e) { return Number((String(e.title).match(/(?:episode|ep|^)\s*(\d+)/i) || [])[1]) === episode; }) || eps2[episode - 1];
    link = hit2 && hit2.link;
  }
  if (!link) return [];
  var streams = (await streamMod.getStream({ link: link, type: (meta && meta.type) || 'series', signal: undefined, providerContext: providerContext })) || [];
  return streams.filter(function (s) { return s && s.link && /^https?:/.test(s.link); });
}

async function getStreams(tmdbId, mediaType, season, episode) {
  try {
    var targets = await resolveTargets(tmdbId, mediaType, season, episode);
    for (var i = 0; i < targets.length; i++) {
      var t = targets[i];
      var titles = await titlesFor(t);
      if (!titles.length) continue;
      var wantSeason = Math.max.apply(null, titles.map(seasonFromTitle).concat([1]));
      var raw = [];
      try { raw = await fromProvider(titles, wantSeason, Number(t.episode) || 1); } catch (e) { raw = []; }
      var out = [], seen = {};
      raw.forEach(function (s) {
        if (seen[s.link]) return; seen[s.link] = 1;
        var audio = audioOf(s.server);
        if (PROVIDER_KEY === 'kickAssAnime' && audio === 'Original') audio = 'Sub';
        var subs = [];
        (s.subtitles || []).forEach(function (x) { var u = unwrapSubtitle(x, s.headers, subs.length); if (u) subs.push(u); });
        subs.sort(function (a, b) { return (b.lang === 'en') - (a.lang === 'en') || a.label.localeCompare(b.label); });
        out.push({
          name: LABEL + ' \u00b7 ' + audio + ' \u00b7 ' + qualityLabel(s.quality),
          title: LABEL + ' \u00b7 ' + s.server,
          url: s.link,
          quality: qualityLabel(s.quality),
          language: /dub/i.test(audio) ? 'English' : audio === 'Hindi' ? 'Hindi' : 'Japanese',
          provider: LABEL,
          type: /m3u8|hls/i.test(String(s.type)) || /\.m3u8/.test(s.link) ? 'hls' : /dash|mpd/i.test(String(s.type)) ? 'dash' : 'mp4',
          headers: s.headers || {},
          subtitles: subs
        });
      });
      if (out.length) return out;
    }
    return [];
  } catch (e) {
    console.log('[cinemaos-kickAssAnime] ' + (e && e.message ? e.message : e));
    return [];
  }
}

module.exports = { getStreams: getStreams };

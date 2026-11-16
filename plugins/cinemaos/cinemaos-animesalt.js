/**
 * CinemaOS - AnimeSalt (animesalt.in) anime (Nuvio QuickJS plugin).
 * Port of the phone app's vega "animesalt" provider bundle (lib/vega/animesalt) driven like lib/scrapers/anime.ts:
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
"use strict";var __defProp=Object.defineProperty,__getOwnPropDesc=Object.getOwnPropertyDescriptor,__getOwnPropNames=Object.getOwnPropertyNames,__hasOwnProp=Object.prototype.hasOwnProperty,__name=(target,value)=>__defProp(target,"name",{value:value,configurable:!0}),__export=(target,all)=>{for(var name in all)__defProp(target,name,{get:all[name],enumerable:!0})},__copyProps=(to,from,except,desc)=>{if(from&&"object"==typeof from||"function"==typeof from)for(let key of __getOwnPropNames(from))__hasOwnProp.call(to,key)||key===except||__defProp(to,key,{get:()=>from[key],enumerable:!(desc=__getOwnPropDesc(from,key))||desc.enumerable});return to},__toCommonJS=mod=>__copyProps(__defProp({},"__esModule",{value:!0}),mod),__async=(__this,__arguments,generator)=>new Promise((resolve,reject)=>{var fulfilled=value=>{try{step(generator.next(value))}catch(e){reject(e)}},rejected=value=>{try{step(generator.throw(value))}catch(e){reject(e)}},step=x=>x.done?resolve(x.value):Promise.resolve(x.value).then(fulfilled,rejected);step((generator=generator.apply(__this,__arguments)).next())}),posts_exports={};__export(posts_exports,{getPosts:()=>getPosts,getSearchPosts:()=>getSearchPosts});var urlsEndpoint="https://raw.githubusercontent.com/Zenda-Cross/vega-providers/refs/heads/main/urls.json",cacheTtl=36e5;function getCache(){const state="undefined"!=typeof providerGlobal&&providerGlobal?providerGlobal:globalThis;return null!=state.__vegaProviderBaseUrlCache__||(state.__vegaProviderBaseUrlCache__={expiresAt:0}),state.__vegaProviderBaseUrlCache__}function fetchProviderUrls(){return __async(this,null,function*(){const cache=getCache();if(cache.data&&Date.now()<cache.expiresAt)return cache.data;if(cache.request)return cache.request;const request=fetch(urlsEndpoint).then(response=>__async(null,null,function*(){if(!response.ok)throw new Error(`URL configuration request failed: ${response.status}`);const data=yield response.json();return console.log("Fetched provider URL configuration"),cache.data=data,cache.expiresAt=Date.now()+cacheTtl,data})).catch(error=>{if(cache.data)return console.warn("Using stale provider URL configuration",error),cache.data;throw error}).finally(()=>{cache.request=void 0});return Object.defineProperty(cache,"request",{configurable:!0,enumerable:!1,value:request,writable:!0}),request})}__name(getCache,"getCache"),__name(fetchProviderUrls,"fetchProviderUrls");var getBaseUrl=__name(providerValue=>__async(null,null,function*(){var _a,_b;try{return null!=(_b=null==(_a=(yield fetchProviderUrls())[providerValue])?void 0:_a.url)?_b:""}catch(error){throw console.error(`Error fetching baseUrl: ${providerValue}`,error),error}}),"getBaseUrl"),defaultHeaders={Referer:"https://www.google.com","User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",Accept:"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8","Accept-Language":"en-US,en;q=0.9",Pragma:"no-cache","Cache-Control":"no-cache"};function getPosts(_0){return __async(this,arguments,function*({filter:filter,page:page=1,signal:signal,providerContext:providerContext}){return fetchPosts({filter:filter,page:page,query:"",signal:signal,providerContext:providerContext})})}function getSearchPosts(_0){return __async(this,arguments,function*({searchQuery:searchQuery,page:page=1,signal:signal,providerContext:providerContext}){return fetchPosts({filter:"",page:page,query:searchQuery,signal:signal,providerContext:providerContext})})}function fetchPosts(_0){return __async(this,arguments,function*({filter:filter,query:query,page:page=1,signal:signal,providerContext:providerContext}){try{if(page>1)return[];const{axios:axios,cheerio:cheerio}=providerContext,baseUrl=((yield getBaseUrl("animesalt").catch(()=>""))||"https://animesalt.in").replace(/\/+$/,"");let url=baseUrl;query&&query.trim()&&(url=`${baseUrl}/search.php?search=${encodeURIComponent(query.trim())}`);const res=yield axios.get(url,{headers:defaultHeaders,signal:signal}),$=cheerio.load(res.data||""),resolveUrl=__name(href=>(null==href?void 0:href.startsWith("http"))?href:new URL(href,baseUrl).href,"resolveUrl");let container=$("body");if(!query)if(filter&&/series/i.test(filter)){const sec=$("h2").filter((_,el)=>/series/i.test($(el).text())).closest("section");sec.length&&(container=sec)}else if(filter&&/movie/i.test(filter)){const sec=$("h2").filter((_,el)=>/movie/i.test($(el).text())).closest("section");sec.length&&(container=sec)}else{const sec=$("h2").filter((_,el)=>/recent/i.test($(el).text())).closest("section");sec.length&&(container=sec)}const seen=new Set,catalog=[],POST_SELECTORS=[".anime-blog","article","li.post",".swiper-slide li",".latest-movies-series-swiper-slide li","ul.post-lst li"].join(",");return container.find(POST_SELECTORS).each((_,el)=>{var _a;const card=$(el);let link=card.find(".action-overlay a[href]").attr("href")||card.find("a.lnk-blk[href]").attr("href")||card.find("a[href*='/series/'], a[href*='/movies/']").first().attr("href")||card.find("a[href]").first().attr("href")||"";if(!link)return;if(link=resolveUrl(link),seen.has(link))return;let title=(null==(_a=card.find("img").attr("alt"))?void 0:_a.trim())||card.find("h2.entry-title").first().text().trim()||card.find(".entry-title").first().text().trim()||card.find("p[style*='medium']").first().text().trim()||card.find("a[href]").last().text().trim()||"";if(title=title.replace(/^Play Now\s*/i,"").replace(/^Image\s+/i,"").trim(),!title)return;let img=card.find("img").attr("data-src")||card.find("img").attr("src")||"";const image=img?resolveUrl(img):"";seen.add(link),catalog.push({title:title,link:link,image:image})}),catalog}catch(err){return console.error("Animesalt fetchPosts error:",err instanceof Error?err.message:String(err)),[]}})}__name(getPosts,"getPosts"),__name(getSearchPosts,"getSearchPosts"),__name(fetchPosts,"fetchPosts"),exports.getPosts=getPosts,exports.getSearchPosts=getSearchPosts;
});
var metaMod = __mod(function (exports, module) {
"use strict";var __defProp=Object.defineProperty,__getOwnPropDesc=Object.getOwnPropertyDescriptor,__getOwnPropNames=Object.getOwnPropertyNames,__hasOwnProp=Object.prototype.hasOwnProperty,__name=(target,value)=>__defProp(target,"name",{value:value,configurable:!0}),__export=(target,all)=>{for(var name in all)__defProp(target,name,{get:all[name],enumerable:!0})},__copyProps=(to,from,except,desc)=>{if(from&&"object"==typeof from||"function"==typeof from)for(let key of __getOwnPropNames(from))__hasOwnProp.call(to,key)||key===except||__defProp(to,key,{get:()=>from[key],enumerable:!(desc=__getOwnPropDesc(from,key))||desc.enumerable});return to},__toCommonJS=mod=>__copyProps(__defProp({},"__esModule",{value:!0}),mod),__async=(__this,__arguments,generator)=>new Promise((resolve,reject)=>{var fulfilled=value=>{try{step(generator.next(value))}catch(e){reject(e)}},rejected=value=>{try{step(generator.throw(value))}catch(e){reject(e)}},step=x=>x.done?resolve(x.value):Promise.resolve(x.value).then(fulfilled,rejected);step((generator=generator.apply(__this,__arguments)).next())}),meta_exports={};__export(meta_exports,{getMeta:()=>getMeta});var headers={Accept:"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,signed-exchange;v=b3;q=0.7","Cache-Control":"no-store","Accept-Language":"en-US,en;q=0.9",DNT:"1",Cookie:"xla=s4t; _ga=GA1.1.1081149560.1756378968; _ga_BLZGKYN5PF=GS2.1.s1756378968$o1$g1$t1756378984$j44$l0$h0","Upgrade-Insecure-Requests":"1","User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"},getMeta=__name(function(_0){return __async(this,arguments,function*({link:link,providerContext:providerContext}){const{axios:axios,cheerio:cheerio}=providerContext,empty={title:"",synopsis:"",image:"",imdbId:"",type:"series",linkList:[]};try{const response=yield axios.get(link,{headers:headers}),$=cheerio.load(response.data),result={title:"",synopsis:"",image:"",imdbId:"",type:"series",linkList:[]};result.title=$(".dfxb.alg-cr .entry-title").text().trim()||$("h1.entry-title").first().text().trim()||$("h1.post-title").first().text().trim()||$("title").text().trim();let image=$(".dfxb.alg-cr img[alt]").attr("src")||$("figure img").first().attr("src")||"";if(image.startsWith("//")&&(image="https:"+image),result.image=image,result.synopsis=$(".description p").text().trim()||$(".entry-content p").first().text().trim(),link.includes("/movies/")||link.includes("/movie/"))return result.type="movie",result.linkList.push({title:result.title||"Play Movie",quality:"Movie",directLinks:[{title:"Play",link:link,type:"movie"}],episodesLink:`watchanimeworld://movie?url=${encodeURIComponent(link)}`}),result;result.type="series";const seasonSet=new Set;$(".season-buttons a.season-btn").each((_,el)=>{const s=parseInt($(el).attr("data-season")||"",10);!isNaN(s)&&s>0&&seasonSet.add(s)}),$(".choose-season .aa-cnt li a").each((_,el)=>{const s=parseInt($(el).attr("data-season")||"",10);!isNaN(s)&&s>0&&seasonSet.add(s)}),$("#episode_by_temp .post.episodes").each((_,el)=>{const match=$(el).find("h2.entry-title").text().trim().match(/(\d+)x(\d+)/);match&&seasonSet.add(parseInt(match[1],10))}),$("a[href*='/episode/']").each((_,el)=>{const match=($(el).attr("href")||"").match(/(\d+)x(\d+)/);match&&seasonSet.add(parseInt(match[1],10))});const seasons=Array.from(seasonSet).sort((a,b)=>a-b);if(0===seasons.length)result.linkList.push({title:result.title||"Watch",quality:"Series",episodesLink:link,directLinks:[]});else for(const s of seasons)result.linkList.push({title:`Season ${s}`,quality:`S${s}`,episodesLink:`watchanimeworld://season?url=${encodeURIComponent(link)}&season=${s}`,directLinks:[]});return result}catch(err){return console.log("Meta error:",err),empty}})},"getMeta");exports.getMeta=getMeta;
});
var episodesMod = __mod(function (exports, module) {
"use strict";var __defProp=Object.defineProperty,__defProps=Object.defineProperties,__getOwnPropDesc=Object.getOwnPropertyDescriptor,__getOwnPropDescs=Object.getOwnPropertyDescriptors,__getOwnPropNames=Object.getOwnPropertyNames,__getOwnPropSymbols=Object.getOwnPropertySymbols,__hasOwnProp=Object.prototype.hasOwnProperty,__propIsEnum=Object.prototype.propertyIsEnumerable,__defNormalProp=(obj,key,value)=>key in obj?__defProp(obj,key,{enumerable:!0,configurable:!0,writable:!0,value:value}):obj[key]=value,__spreadValues=(a,b)=>{for(var prop in b||(b={}))__hasOwnProp.call(b,prop)&&__defNormalProp(a,prop,b[prop]);if(__getOwnPropSymbols)for(var prop of __getOwnPropSymbols(b))__propIsEnum.call(b,prop)&&__defNormalProp(a,prop,b[prop]);return a},__spreadProps=(a,b)=>__defProps(a,__getOwnPropDescs(b)),__name=(target,value)=>__defProp(target,"name",{value:value,configurable:!0}),__export=(target,all)=>{for(var name in all)__defProp(target,name,{get:all[name],enumerable:!0})},__copyProps=(to,from,except,desc)=>{if(from&&"object"==typeof from||"function"==typeof from)for(let key of __getOwnPropNames(from))__hasOwnProp.call(to,key)||key===except||__defProp(to,key,{get:()=>from[key],enumerable:!(desc=__getOwnPropDesc(from,key))||desc.enumerable});return to},__toCommonJS=mod=>__copyProps(__defProp({},"__esModule",{value:!0}),mod),__async=(__this,__arguments,generator)=>new Promise((resolve,reject)=>{var fulfilled=value=>{try{step(generator.next(value))}catch(e){reject(e)}},rejected=value=>{try{step(generator.throw(value))}catch(e){reject(e)}},step=x=>x.done?resolve(x.value):Promise.resolve(x.value).then(fulfilled,rejected);step((generator=generator.apply(__this,__arguments)).next())}),episodes_exports={};__export(episodes_exports,{getEpisodes:()=>getEpisodes});var headers={Accept:"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8","Accept-Language":"en-US,en;q=0.9","User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"},getEpisodes=__name(function(_0){return __async(this,arguments,function*({url:url,providerContext:providerContext}){var _a;const{axios:axios,cheerio:cheerio}=providerContext;let pageUrl=url,targetSeason=0,isMovie=!1;if(url.startsWith("watchanimeworld://movie"))try{const u=new URL(url);pageUrl=decodeURIComponent(u.searchParams.get("url")||""),isMovie=!0}catch(e){return console.log("getEpisodes: failed to parse movie URL",e),[]}else if(url.startsWith("watchanimeworld://season"))try{const u=new URL(url);pageUrl=decodeURIComponent(u.searchParams.get("url")||""),targetSeason=parseInt(u.searchParams.get("season")||"0",10)}catch(e){return console.log("getEpisodes: failed to parse season URL",e),[]}else(url.includes("/movies/")||url.includes("/movie/"))&&(isMovie=!0,pageUrl=url);if(!pageUrl)return console.log("getEpisodes: missing page URL"),[];console.log(`getEpisodes: fetching ${pageUrl}, ${isMovie?"MOVIE":`season: ${targetSeason||"all"}`}`);try{const res=yield axios.get(pageUrl,{headers:headers}),$=cheerio.load(res.data),origin=new URL(pageUrl).origin;if(isMovie){const movieLinks=[];return[".server-list li a",".servers-list li a","ul#player-list li a",".mirror",".play-button a",".player-options li a",".options-list li a"].forEach(sel=>{$(sel).each((_,el)=>{var _a2;const href=$(el).attr("href")||$(el).attr("data-url")||"";if(!href)return;const full=href.startsWith("http")?href:new URL(href,pageUrl).href;if(full===pageUrl||full.endsWith("#"))return;const label=$(el).text().trim()||(null==(_a2=$(el).attr("title"))?void 0:_a2.trim())||"Play Movie";movieLinks.find(m=>m.link===full)||movieLinks.push({title:label,link:full})})}),0===movieLinks.length&&movieLinks.push({title:"Play",link:pageUrl}),console.log(`getEpisodes: found ${movieLinks.length} movie links`),movieLinks}const postId=$(".season-buttons a.season-btn[data-post]").first().attr("data-post")||$("a.season-btn[data-post]").first().attr("data-post")||(null==(_a=$("article.post").attr("id"))?void 0:_a.replace("post-",""))||"",extractEpisodeInfo=__name((titleText,numEpiText)=>{const titleMatch=titleText.match(/(\d+)x(\d+)/);if(titleMatch)return{season:parseInt(titleMatch[1],10),episode:parseInt(titleMatch[2],10)};const epNum=parseInt(numEpiText.trim(),10);return isNaN(epNum)?null:{season:targetSeason||1,episode:epNum}},"extractEpisodeInfo");let episodes=[];if(postId&&targetSeason)try{const ajaxUrl=`${origin}/wp-admin/admin-ajax.php`,ajaxRes=yield axios.get(ajaxUrl,{params:{action:"action_select_season",season:targetSeason,post:postId},headers:__spreadProps(__spreadValues({},headers),{Referer:pageUrl,"X-Requested-With":"XMLHttpRequest"})});let ajaxHtml="";const data=ajaxRes.data;if("string"==typeof data?ajaxHtml=data:data&&"object"==typeof data&&(ajaxHtml=data.data||data.html||data.episodes||"","string"!=typeof ajaxHtml&&(ajaxHtml="")),ajaxHtml.trim()){const $$=cheerio.load(ajaxHtml);$$("li article.episodes, li article.post.episodes").each((_,el)=>{const $art=$$(el),href=$art.find("a.lnk-blk").attr("href")||$art.find("a").first().attr("href");if(!href)return;const titleText=$art.find("h2.entry-title").text().trim(),numEpiText=$art.find(".num-epi").text().trim(),info=extractEpisodeInfo(titleText,numEpiText);info&&(targetSeason&&info.season!==targetSeason||episodes.push({title:`Episode ${info.episode}`,link:href.startsWith("http")?href:new URL(href,pageUrl).href}))})}}catch(ajaxErr){console.log("getEpisodes: AJAX attempt failed:",ajaxErr)}0===episodes.length&&$("#episode_by_temp li").each((_,el)=>{const $art=$(el).find("article.episodes, article.post.episodes");if(!$art.length)return;const href=$art.find("a.lnk-blk").attr("href");if(!href)return;const titleText=$art.find("h2.entry-title").text().trim(),numEpiText=$art.find(".num-epi").text().trim(),info=extractEpisodeInfo(titleText,numEpiText);info&&(targetSeason&&info.season!==targetSeason||episodes.push({title:`Episode ${info.episode}`,link:href.startsWith("http")?href:new URL(href,pageUrl).href}))}),0===episodes.length&&$("a[href*='/episode/']").each((_,el)=>{const href=$(el).attr("href");if(!href)return;const full=href.startsWith("http")?href:new URL(href,pageUrl).href,m=full.match(/(\d+)x(\d+)/i)||full.match(/episode[/-](\d+)/i);let epNum=0,sNum=1;if(m&&(m[2]?(sNum=parseInt(m[1],10),epNum=parseInt(m[2],10)):epNum=parseInt(m[1],10)),targetSeason&&sNum&&sNum!==targetSeason)return;const label=epNum?`Episode ${epNum}`:$(el).text().trim()||"Episode";episodes.push({title:label,link:full})}),episodes.sort((a,b)=>{var _a2,_b;return parseInt((null==(_a2=a.title.match(/\d+/))?void 0:_a2[0])||"0",10)-parseInt((null==(_b=b.title.match(/\d+/))?void 0:_b[0])||"0",10)});const seen=new Set;return episodes=episodes.filter(e=>!seen.has(e.link)&&(seen.add(e.link),!0)),console.log(`getEpisodes: found ${episodes.length} episodes for season ${targetSeason}`),episodes}catch(err){return console.log("getEpisodes error:",err),[]}})},"getEpisodes");exports.getEpisodes=getEpisodes;
});
var streamMod = __mod(function (exports, module) {
"use strict";var __defProp=Object.defineProperty,__defProps=Object.defineProperties,__getOwnPropDesc=Object.getOwnPropertyDescriptor,__getOwnPropDescs=Object.getOwnPropertyDescriptors,__getOwnPropNames=Object.getOwnPropertyNames,__getOwnPropSymbols=Object.getOwnPropertySymbols,__hasOwnProp=Object.prototype.hasOwnProperty,__propIsEnum=Object.prototype.propertyIsEnumerable,__defNormalProp=(obj,key,value)=>key in obj?__defProp(obj,key,{enumerable:!0,configurable:!0,writable:!0,value:value}):obj[key]=value,__spreadValues=(a,b)=>{for(var prop in b||(b={}))__hasOwnProp.call(b,prop)&&__defNormalProp(a,prop,b[prop]);if(__getOwnPropSymbols)for(var prop of __getOwnPropSymbols(b))__propIsEnum.call(b,prop)&&__defNormalProp(a,prop,b[prop]);return a},__spreadProps=(a,b)=>__defProps(a,__getOwnPropDescs(b)),__name=(target,value)=>__defProp(target,"name",{value:value,configurable:!0}),__export=(target,all)=>{for(var name in all)__defProp(target,name,{get:all[name],enumerable:!0})},__copyProps=(to,from,except,desc)=>{if(from&&"object"==typeof from||"function"==typeof from)for(let key of __getOwnPropNames(from))__hasOwnProp.call(to,key)||key===except||__defProp(to,key,{get:()=>from[key],enumerable:!(desc=__getOwnPropDesc(from,key))||desc.enumerable});return to},__toCommonJS=mod=>__copyProps(__defProp({},"__esModule",{value:!0}),mod),__async=(__this,__arguments,generator)=>new Promise((resolve,reject)=>{var fulfilled=value=>{try{step(generator.next(value))}catch(e){reject(e)}},rejected=value=>{try{step(generator.throw(value))}catch(e){reject(e)}},step=x=>x.done?resolve(x.value):Promise.resolve(x.value).then(fulfilled,rejected);step((generator=generator.apply(__this,__arguments)).next())}),stream_exports={};__export(stream_exports,{getStream:()=>getStream});var UA="Mozilla/5.0 (Linux; Android 16; Pixel 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Mobile Safari/537.36";function buildPlaybackHeaders(){return{"User-Agent":UA,Accept:"*/*","Accept-Language":"en-GB,en-US;q=0.9,en;q=0.8,hi;q=0.7",Referer:"https://playhydrax.com/"}}function makeAbsolute(url,base){if(!url)return"";if(url.startsWith("http://")||url.startsWith("https://"))return url;const b=new URL(base);return url.startsWith("/")?`${b.protocol}//${b.host}${url}`:`${b.protocol}//${b.host}/${url}`}function fetchCuidCookie(axios){return __async(this,null,function*(){var _a;try{const res=yield axios.post("https://meatusbyway.cfd/cuid/?f=https%3A%2F%2Fwatchanimeworld.one","{}",{headers:{Accept:"application/json","Content-Type":"application/json",Origin:"https://watchanimeworld.one",Referer:"https://watchanimeworld.one/","User-Agent":UA},timeout:5e3}),key=null==(_a=null==res?void 0:res.data)?void 0:_a.key;return key?`a97fa794a0f9=${key}`:""}catch(e){return""}})}function promiseAny(promises){return new Promise((resolve,reject)=>{let rejectedCount=0;if(0===promises.length)return reject(new Error("Empty promise array"));promises.forEach(p=>{Promise.resolve(p).then(resolve,()=>{rejectedCount++,rejectedCount===promises.length&&reject(new Error("All promises rejected"))})})})}function parseConfigLangs(pageHtml){const out=[],m=pageHtml.match(/var CONFIG = (\{[\s\S]*?\});/);if(!m)return out;try{const config=JSON.parse(m[1]),ready=config.ready||{},langMap=config.lang||{};for(const code of Object.keys(ready)){const info=langMap[code]||{};out.push({code:code,name:info.name||code,short:info.short||code.slice(0,2).toUpperCase(),slug:ready[code]})}}catch(e){console.warn("[abbasy] CONFIG parse failed",e)}return out}function fetchSourcesForSlug(slug,cuidCookie,axios){return __async(this,null,function*(){const playerUrl=`https://player.abyssplayer.com/${slug}`,datasMatch=(yield axios.get(playerUrl,{headers:__spreadValues({"User-Agent":UA,Referer:"https://watchanimeworld.one/"},cuidCookie?{Cookie:cuidCookie}:{}),timeout:8e3})).data.match(/const\s+datas\s*=\s*["']([A-Za-z0-9+/=]+)["']/);if(!datasMatch)return[];const datasBlob=datasMatch[1],payloads=[{data:datasBlob},{datas:datasBlob},{text:datasBlob},{url:playerUrl},{slug:slug}],apiRes=yield promiseAny(payloads.map(p=>axios.post("https://enc-dec.app/api/dec-abyss",p,{headers:{"Content-Type":"application/json","User-Agent":UA,Referer:"https://enc-dec.app/"},timeout:1e4}).then(r=>{if(!(null==r?void 0:r.data))throw new Error("empty");return r}))).catch(()=>null);if(!(null==apiRes?void 0:apiRes.data))return[];const data=apiRes.data,sources=[];return(null==data?void 0:data.result)&&Array.isArray(data.result.sources)?sources.push(...data.result.sources):Array.isArray(null==data?void 0:data.sources)?sources.push(...data.sources):Array.isArray(data)&&sources.push(...data),sources})}function abyssExtractor(link,_signal,axios,_cheerio,headers,_providerContext){return __async(this,null,function*(){const streamLinks=[],t0=Date.now();try{let pageUrl=link;pageUrl.startsWith("http")||(pageUrl=makeAbsolute(pageUrl,"https://watchanimeworld.one/")),console.log(`[abbasy] → ${pageUrl}`);const cuidPromise=fetchCuidCookie(axios);let langs=[];if(langs=pageUrl.includes("player.abyssplayer.com")?[{code:"und",name:"Unknown",short:"UN",slug:pageUrl.split("/").pop()||""}]:parseConfigLangs((yield axios.get(pageUrl,{headers:{"User-Agent":UA,Referer:"https://watchanimeworld.one/",Origin:"https://watchanimeworld.one"},timeout:8e3})).data),!langs.length)return[{server:"AbyssPlayer",link:pageUrl,type:"iframe",headers:{},subtitles:[]}];const cuidCookie=yield cuidPromise,langsWithSources=yield Promise.all(langs.map(lang=>__async(null,null,function*(){try{const sources=yield fetchSourcesForSlug(lang.slug,cuidCookie,axios);return{lang:lang,sources:sources}}catch(e){return console.warn(`[abbasy] ${lang.name} failed:`,e),{lang:lang,sources:[]}}}))),codecRank=__name(c=>"h264"===(null==c?void 0:c.toLowerCase())?0:1,"codecRank"),resRank=__name(t=>parseInt((null==t?void 0:t.replace(/[^\d]/g,""))||"0",10),"resRank"),playbackHeaders=buildPlaybackHeaders();for(const{lang:lang,sources:sources}of langsWithSources)if(sources.length){sources.sort((a,b)=>codecRank(a.codec)-codecRank(b.codec)||resRank(a.type)-resRank(b.type));for(const src of sources){if(!(null==src?void 0:src.url)||!1===src.status)continue;const srcLang=src.language||src.lang||src.audio_lang||lang.name,srcLangShort=src.language_short||src.lang_short||lang.short;streamLinks.push({server:`AbyssPlayer (${srcLang})`,link:src.url,type:src.url.includes(".m3u8")?"m3u8":"mp4",headers:playbackHeaders,subtitles:[],quality:`${srcLangShort} · ${src.type||"auto"}${src.codec?` · ${src.codec}`:""}`})}}return streamLinks.length||streamLinks.push({server:"AbyssPlayer",link:`https://player.abyssplayer.com/${langs[0].slug}`,type:"iframe",headers:{},subtitles:[]}),console.log(`[abbasy] ${streamLinks.length} streams in ${Date.now()-t0}ms`),streamLinks}catch(err){return console.error("[abbasy] error:",(null==err?void 0:err.message)||err),[]}})}function zypterExtractor(link,signal,axios,cheerio,headers,providerContext){return __async(this,null,function*(){var _a,_b;const streamLinks=[];try{const dataMatch=link.match(/\/video\/([a-zA-Z0-9_-]+)/)||link.match(/\/([a-zA-Z0-9_-]+)\/?$/),dataId=dataMatch?dataMatch[1]:link.split("/").filter(Boolean).pop();if(!dataId)return console.warn("[zypter] no data ID in URL"),[];const urlObj=new URL(link),baseUrl=`${urlObj.protocol}//${urlObj.host}`,apiUrl=`${baseUrl}/player/index.php?data=${dataId}&do=getVideo`,apiHeaders={Accept:"*/*","Content-Type":"application/x-www-form-urlencoded; charset=UTF-8",Origin:baseUrl,Referer:link,"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36","X-Requested-With":"XMLHttpRequest"},body=new URLSearchParams({hash:dataId,r:baseUrl}),apiRes=yield axios.post(apiUrl,body,{headers:apiHeaders,timeout:1e4}),streamUrl=(null==(_a=null==apiRes?void 0:apiRes.data)?void 0:_a.securedLink)||(null==(_b=null==apiRes?void 0:apiRes.data)?void 0:_b.videoSource);return streamUrl?(streamLinks.push({server:"ZephyrFlick",link:streamUrl,type:streamUrl.includes(".m3u8")?"m3u8":"mp4",headers:{Referer:baseUrl,Origin:baseUrl,"User-Agent":apiHeaders["User-Agent"]},subtitles:[]}),console.log(`[zypter] queued ${streamUrl.slice(0,80)}…`),streamLinks):(console.warn("[zypter] no stream in response"),[])}catch(err){return console.error("[zypter] error:",(null==err?void 0:err.message)||err),[]}})}function makeAbsolute2(url,base){if(!url)return"";if(url.startsWith("http://")||url.startsWith("https://"))return url;const b=new URL(base);return url.startsWith("/")?`${b.protocol}//${b.host}${url}`:`${b.protocol}//${b.host}/${url}`}function decodeBase64(input){try{if("function"==typeof atob){const binary=atob(input),bytes=new Uint8Array(binary.length);for(let i=0;i<binary.length;i++)bytes[i]=binary.charCodeAt(i);return(new TextDecoder).decode(bytes)}}catch(e){}try{return Buffer.from(input,"base64").toString("utf-8")}catch(e){return""}}__name(buildPlaybackHeaders,"buildPlaybackHeaders"),__name(makeAbsolute,"makeAbsolute"),__name(fetchCuidCookie,"fetchCuidCookie"),__name(promiseAny,"promiseAny"),__name(parseConfigLangs,"parseConfigLangs"),__name(fetchSourcesForSlug,"fetchSourcesForSlug"),__name(abyssExtractor,"abyssExtractor"),__name(zypterExtractor,"zypterExtractor"),__name(makeAbsolute2,"makeAbsolute"),__name(decodeBase64,"decodeBase64");var ZYPTER_HOSTS=["zephyrix.org","zephyr","ravok.buzz","ravok"],ABYSS_MARKERS=["abyssplayer","abysssplayer","dub-player","/plyr/player.php"],isZypterUrl=__name(u=>ZYPTER_HOSTS.some(h=>u.includes(h)),"isZypterUrl"),isAbyssUrl=__name(u=>ABYSS_MARKERS.some(m=>u.includes(m)),"isAbyssUrl"),isPlyrWrapper=__name(u=>u.includes("player.php?data="),"isPlyrWrapper");function plyrWrapperExtractor(playerUrl,signal,axios,cheerio,headers,providerContext){return __async(this,null,function*(){const options=[];try{const data=new URL(playerUrl).searchParams.get("data");if(data){const decoded=decodeBase64(data);if(decoded){const parsed=JSON.parse(decoded);if(Array.isArray(parsed))for(const item of parsed)item&&item.link&&options.push({language:String(item.language||"").trim(),link:String(item.link).trim()})}}}catch(e){console.log("plyrWrapperExtractor: data decode failed",e)}if(0===options.length)try{const res=yield axios.get(playerUrl,{headers:headers,signal:signal,timeout:8e3}),$=cheerio.load(res.data||"");$(".modal-option").each((_,el)=>{var _a,_b;const link=null==(_a=$(el).attr("data-link"))?void 0:_a.trim(),language=(null==(_b=$(el).attr("data-language"))?void 0:_b.trim())||"";link&&options.push({language:language,link:link})})}catch(e){console.log("plyrWrapperExtractor: HTML fetch failed",e)}if(0===options.length)return console.log("plyrWrapperExtractor: no audio options found"),[];console.log(`plyrWrapperExtractor: found ${options.length} audio options →`,options.map(o=>o.language||o.link));const results=yield Promise.allSettled(options.map(opt=>abyssExtractor(opt.link,signal,axios,cheerio,headers,providerContext).then(streams=>streams.map(s=>__spreadProps(__spreadValues({},s),{server:opt.language?`${opt.language} • ${s.server}`:s.server}))))),merged=[];for(const r of results)"fulfilled"===r.status&&Array.isArray(r.value)&&merged.push(...r.value);const seen=new Set,unique=merged.filter(s=>!(!s.link||seen.has(s.link)||(seen.add(s.link),0)));return console.log(`plyrWrapperExtractor: ${unique.length} total streams`),unique})}__name(plyrWrapperExtractor,"plyrWrapperExtractor");var getEpisodeStreams=__name(function(_0){return __async(this,arguments,function*({url:url,providerContext:providerContext}){const{axios:axios,cheerio:cheerio,commonHeaders:headers}=providerContext,t0=Date.now();try{const res=yield axios.get(url,{headers:headers,timeout:8e3}),$=cheerio.load(res.data),iframeUrls=[],pick=__name(sel=>{$(sel).each((_,el)=>{const src=$(el).attr("data-src")||$(el).attr("src")||"";if(src){const abs=makeAbsolute2(src,url);iframeUrls.includes(abs)||iframeUrls.push(abs)}})},"pick");if(pick(".video-box iframe"),pick("#responsiveIframe"),pick("section.video iframe"),pick(".video-content iframe"),pick("#options-0 iframe"),pick("#options-1 iframe"),pick(".video-player iframe"),pick("#aa-options iframe"),pick("aside.video-player iframe"),0===iframeUrls.length&&$("iframe").each((_,el)=>{const src=$(el).attr("data-src")||$(el).attr("src")||"";if(src&&!/google|doubleclick|highrevenueformat|adservice|disqus|llvpn/i.test(src)){const abs=makeAbsolute2(src,url);iframeUrls.includes(abs)||iframeUrls.push(abs)}}),!iframeUrls.length)return[];const signal=(new AbortController).signal,results=yield Promise.allSettled(iframeUrls.map(u=>isPlyrWrapper(u)?plyrWrapperExtractor(u,signal,axios,cheerio,headers,providerContext):isZypterUrl(u)?zypterExtractor(u,signal,axios,cheerio,headers,providerContext):abyssExtractor(u,signal,axios,cheerio,headers,providerContext))),merged=[];for(const r of results)"fulfilled"===r.status&&Array.isArray(r.value)&&merged.push(...r.value);return console.log(`[episode] ${merged.length} streams in ${Date.now()-t0}ms`),merged.length?merged.map(s=>__spreadProps(__spreadValues({},s),{server:"ZephyrFlick"===s.server?"HLS (Multi-Audio)":s.server})):iframeUrls.length>0&&!iframeUrls[0].includes("/episode/")&&!iframeUrls[0].includes("/movies/")?[{server:"AnimeSalt (Embed)",link:iframeUrls[0],type:"iframe",headers:{},subtitles:[]}]:[]}catch(err){return console.error("[episode] error:",(null==err?void 0:err.message)||err),[]}})},"getEpisodeStreams"),getStream=__name(function(_0){return __async(this,arguments,function*({link:url,providerContext:providerContext}){console.log("[getStream]",url);const{axios:axios,cheerio:cheerio,commonHeaders:headers}=providerContext,signal=(new AbortController).signal;return url.includes("/episode/")||url.includes("/movies/")||url.includes("/movie/")?getEpisodeStreams({url:url,providerContext:providerContext}):isPlyrWrapper(url)?plyrWrapperExtractor(url,signal,axios,cheerio,headers,providerContext):isZypterUrl(url)?zypterExtractor(url,signal,axios,cheerio,headers,providerContext):isAbyssUrl(url)?abyssExtractor(url,signal,axios,cheerio,headers,providerContext):zypterExtractor(url,signal,axios,cheerio,headers,providerContext)})},"getStream");exports.getStream=getStream;
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
var PROVIDER_KEY = 'animesalt', LABEL = 'AnimeSalt';
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
    console.log('[cinemaos-animesalt] ' + (e && e.message ? e.message : e));
    return [];
  }
}

module.exports = { getStreams: getStreams };

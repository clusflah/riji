package app.birthdayreminder.ui.diary

import app.birthdayreminder.data.DiaryLocation

/**
 * 选点页面用的 HTML（跑在 WebView 里，用高德 JS API：搜索、点地图选点、逆地理编码）。
 * 复用你在旧版里已经申请好的「Web端(JS API)」Key 和安全密钥，不用再去申请 Android 平台的 Key。
 * 页面通过 Android.onPicked / Android.requestLocation / Android.onError 和原生通信。
 */
object MapHtml {

    /** 把任意字符串变成安全的 JS 字符串字面量（防止 Key/地址里的引号、</script> 破坏页面） */
    fun jsStr(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '\\' -> sb.append("\\\\")
                c == '"' -> sb.append("\\\"")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '<' -> sb.append("\\u003c")
                c == '\u2028' -> sb.append("\\u2028")
                c == '\u2029' -> sb.append("\\u2029")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    fun build(key: String, secCode: String, dark: Boolean, init: DiaryLocation?): String {
        val initJs = if (init == null) "null"
        else "{lat:${init.lat},lng:${init.lng},address:${jsStr(init.address)}}"
        val scriptUrl = "https://webapi.amap.com/maps?v=2.0&key=" +
            java.net.URLEncoder.encode(key, "UTF-8") + "&plugin=AMap.Geocoder,AMap.PlaceSearch"
        return TEMPLATE
            .replace("__DARK__", if (dark) "dark" else "light")
            .replace("__SEC__", if (secCode.isBlank()) "null" else "{securityJsCode:${jsStr(secCode)}}")
            .replace("__SCRIPT_URL__", scriptUrl)
            .replace("__INIT__", initJs)
    }

    private val TEMPLATE = """<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>
html,body{margin:0;height:100%;font-family:sans-serif}
body.light{background:#fff;color:#333;--line:#e5e5ea;--soft:#f3f3f7;--muted:#888}
body.dark{background:#1a1a1f;color:#ddd;--line:#33333a;--soft:#26262c;--muted:#9a9aa5}
#wrap{display:flex;flex-direction:column;height:100%}
.hint{padding:10px 14px;color:var(--muted);font-size:13px}
#map{flex:1;min-height:160px}
#addr{padding:10px 14px;font-size:13px;color:var(--muted);border-top:1px solid var(--line);min-height:18px}
</style></head>
<body class="__DARK__">
<div id="wrap">
  <div id="map"></div>
  <div id="addr">搜索地点，或点击地图选择</div>
</div>
<script>window._AMapSecurityConfig = __SEC__;</script>
<script src="__SCRIPT_URL__" onerror="window.__amapFail=true"></script>
<script>
var INIT = __INIT__;
var map = null, marker = null, ps = null, geoToken = 0;
function el(id){ return document.getElementById(id); }
function say(t){ el('addr').textContent = t; }
function report(lat, lng, addr){ if (window.Android) Android.onPicked(lat, lng, addr); }
function fail(msg){
  el('map').innerHTML = '<div class="hint">' + msg + '</div>';
  if (window.Android) Android.onError(msg);
}
function setMarker(lng, lat){
  if (marker) { marker.setPosition([lng, lat]); }
  else { marker = new AMap.Marker({position: [lng, lat], map: map}); }
}
function pickPoint(lng, lat, known){
  setMarker(lng, lat);
  var first = known || (lat.toFixed(5) + ', ' + lng.toFixed(5));
  say(first); report(lat, lng, first);
  if (known) return;
  var token = ++geoToken;
  AMap.plugin(['AMap.Geocoder'], function(){
    new AMap.Geocoder().getAddress([lng, lat], function(status, result){
      if (token !== geoToken) return;
      if (status === 'complete' && result.regeocode && result.regeocode.formattedAddress) {
        var a = result.regeocode.formattedAddress;
        say(a); report(lat, lng, a);
      } else if (status === 'error') {
        say(first + '（地址解析失败：' + errText(result).replace('搜索失败：', '') + '）');
      }
    });
  });
}
function errText(info){
  var i = String(info && info.info ? info.info : info || '');
  if (i.indexOf('INVALID_USER_SCODE') >= 0) return '安全密钥（JSCode）没填或不对，请到 设置 → 高德地图 里检查';
  if (i.indexOf('USERKEY_PLAT_NOMATCH') >= 0 || i.indexOf('INVALID_USER_KEY') >= 0 || i.indexOf('INVALID_USER_DOMAIN') >= 0) return 'Key 不对或类型不对：需要在高德控制台申请“Web端(JS API)”类型的 Key';
  if (i.indexOf('OVER_LIMIT') >= 0 || i.indexOf('TOO_FREQUENT') >= 0) return '请求太频繁或今天的免费额度用完了，稍后再试';
  return '搜索失败：' + (i || '未知原因');
}
function nativeReply(o){ if (window.Android && Android.onSearchResult) Android.onSearchResult(JSON.stringify(o)); }
// 由原生界面调用：搜索框、按钮、结果列表都在原生那边，这里只负责去问高德，再把结果交回去
window.nativeSearch = function(kw){
  var done = false;
  var timer = setTimeout(function(){ if (!done) { done = true; nativeReply({ok: false, msg: '搜索超时，请检查网络后重试'}); } }, 12000);
  function finish(){ done = true; clearTimeout(timer); }
  function fail(e){ finish(); nativeReply({ok: false, msg: '搜索出错：' + (e && e.message ? e.message : e)}); }
  try {
    AMap.plugin(['AMap.PlaceSearch'], function(){
      if (done) return;
      try {
        if (!ps) ps = new AMap.PlaceSearch({pageSize: 8});
        ps.search(kw, function(status, result){
          if (done) return;
          finish();
          if (status === 'error') { nativeReply({ok: false, msg: errText(result)}); return; }
          var pois = (status === 'complete' && result && result.poiList && result.poiList.pois) ? result.poiList.pois : [];
          var items = [];
          pois.forEach(function(p){
            if (!p.location) return;
            items.push({name: p.name, address: (typeof p.address === 'string' && p.address) ? p.address : (p.district || ''), lat: p.location.lat, lng: p.location.lng});
          });
          nativeReply({ok: true, items: items});
        });
      } catch (e) { fail(e); }
    });
  } catch (e) { fail(e); }
  return 1;
};
// 用户在原生结果列表里选了一个地点：地图跳过去并打上标记
window.nativePick = function(lat, lng, label){
  if (!map) return;
  map.setZoomAndCenter(16, [lng, lat]);
  pickPoint(lng, lat, label);
};
// 手机 GPS 给的是 WGS84 坐标，高德地图用 GCJ-02，直接用会偏几百米，先转换
window.onNativeLocation = function(lat, lng){
  if (!map) return;
  var use = function(lo, la){ map.setZoomAndCenter(16, [lo, la]); pickPoint(lo, la, null); };
  if (window.AMap && typeof AMap.convertFrom === 'function') {
    AMap.convertFrom([lng, lat], 'gps', function(status, result){
      if (status === 'complete' && result.locations && result.locations.length) {
        use(result.locations[0].getLng(), result.locations[0].getLat());
      } else { use(lng, lat); }
    });
  } else { use(lng, lat); }
};
window.onNativeLocationFailed = function(msg){ say(msg); };
function boot(){
  if (!window.AMap || window.__amapFail) { fail('地图加载失败，请检查网络、Key 和安全密钥'); return; }
  var center = INIT ? [INIT.lng, INIT.lat] : [116.397428, 39.90923];
  map = new AMap.Map('map', {zoom: 15, center: center});
  if (INIT) { setMarker(INIT.lng, INIT.lat); say(INIT.address); }
  map.on('click', function(e){ pickPoint(e.lnglat.getLng(), e.lnglat.getLat(), null); });
}
window.addEventListener('load', boot);
</script>
</body></html>"""
}

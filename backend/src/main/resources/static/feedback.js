// 추천 피드백 전송 헬퍼. 익명 방문자 id 를 localStorage 에 두고 fire-and-forget 으로 보낸다.
(function (w) {
  function clientId() {
    let id = localStorage.getItem('sgr_client_id');
    if (!id) {
      id = (w.crypto && w.crypto.randomUUID)
        ? w.crypto.randomUUID()
        : (String(Date.now()) + Math.random().toString(16).slice(2));
      localStorage.setItem('sgr_client_id', id);
    }
    return id;
  }

  // signal: 'UP' | 'DOWN' | 'CLICK', context: 'INPUT'|'PROFILE'|'RECENT'|'SIMILAR'|'RANDOM'
  w.sgrFeedback = function (appid, signal, context, rankPosition, steamId) {
    try {
      fetch('/api/recommend/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        keepalive: true, // CLICK 시 페이지 이동에도 전송 보장
        body: JSON.stringify({
          clientId: clientId(),
          appid: appid,
          signal: signal,
          context: context,
          rankPosition: rankPosition,
          steamId: (steamId && String(steamId).trim()) || null
        })
      }).catch(function () {}); // UX 를 막지 않도록 실패 무시
    } catch (e) { /* no-op */ }
  };
})(window);

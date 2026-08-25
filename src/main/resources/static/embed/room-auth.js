// room.html <head> 최상단, 리액트 번들보다 먼저 삽입해서 쓸 것:
// <script src="{서버 주소}/embed/room-auth.js"></script>
// 로컬 개발: http://localhost:8080/embed/room-auth.js
(function () {
  'use strict';

  var sessionToken = null;

  // URL 쿼리스트링에서 roomId 추출
  function getRoomIdFromUrl() {
    var params = new URLSearchParams(window.location.search);
    return params.get('roomId');
  }

  // sdk.js가 보낸 INIT 메시지 수신. origin은 document.referrer 자기일관성으로 검증.
  // 수신 성공 시 'room-auth:ready' 커스텀 이벤트 발생 (detail: { sessionToken, roomId })
  window.addEventListener('message', function (event) {
    try {
      if (new URL(document.referrer).origin !== event.origin) return;
    } catch (e) {
      return;
    }
    if (!event.data || event.data.type !== 'INIT') return;

    sessionToken = event.data.sessionToken;
    window.dispatchEvent(new CustomEvent('room-auth:ready', {
      detail: { sessionToken: sessionToken, roomId: getRoomIdFromUrl() }
    }));
  });

  // 결제 페이지로 이동. 인자: chatMessageId(결제할 대상 메시지 id)
  window.submitPayment = function submitPayment(chatMessageId) {
    if (!sessionToken) {
      console.error('room-auth: sessionToken이 아직 준비되지 않았습니다.');
      return;
    }

    var form = document.createElement('form');
    form.method = 'POST';
    form.action = '/pay';
    form.target = '_top';
    form.style.display = 'none';

    var tokenInput = document.createElement('input');
    tokenInput.type = 'hidden';
    tokenInput.name = 'sessionToken';
    tokenInput.value = sessionToken;
    form.appendChild(tokenInput);

    var messageIdInput = document.createElement('input');
    messageIdInput.type = 'hidden';
    messageIdInput.name = 'chatMessageId';
    messageIdInput.value = chatMessageId;
    form.appendChild(messageIdInput);

    document.body.appendChild(form);
    form.submit();
  };

  window.getRoomId = getRoomIdFromUrl;
})();

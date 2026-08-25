(function () {
  'use strict';

  // sdk.js 자신의 <script src> URL에서 origin을 추출 (하드코딩 방지)
  var SDK_ORIGIN = (function () {
    var scriptEl = document.currentScript;
    if (!scriptEl) {
      throw new Error('OurChatSDK: <script src="..."> 태그로 로드해야 합니다.');
    }
    return new URL(scriptEl.src).origin;
  })();

  // container(셀렉터 문자열 또는 DOM 엘리먼트)를 받아 실제 DOM 엘리먼트로 변환
  function resolveContainer(container) {
    if (typeof container === 'string') {
      var el = document.querySelector(container);
      if (!el) {
        throw new Error('OurChatSDK: container 셀렉터에 해당하는 요소를 찾을 수 없습니다: ' + container);
      }
      return el;
    }
    return container;
  }

  // 채팅 iframe을 생성해 container에 삽입. { chatRoomId, sessionToken, container } 필수.
  // 반환값: { close } — 위젯 종료용 핸들
  function open(params) {
    params = params || {};
    var chatRoomId = params.chatRoomId;
    var sessionToken = params.sessionToken;

    if (chatRoomId === undefined || chatRoomId === null || chatRoomId === '') {
      console.error('OurChatSDK.open: chatRoomId는 필수입니다.');
      return null;
    }
    if (!sessionToken) {
      console.error('OurChatSDK.open: sessionToken은 필수입니다.');
      return null;
    }
    if (!params.container) {
      console.error('OurChatSDK.open: container는 필수입니다.');
      return null;
    }

    var container = resolveContainer(params.container);

    var iframe = document.createElement('iframe');
    iframe.src = SDK_ORIGIN + '/embed/room.html?roomId=' + encodeURIComponent(chatRoomId);
    iframe.setAttribute(
      'sandbox',
      'allow-scripts allow-forms allow-top-navigation-by-user-activation allow-same-origin'
    );
    iframe.style.border = 'none';

    iframe.addEventListener('load', function onLoad() {
      iframe.removeEventListener('load', onLoad);
      iframe.contentWindow.postMessage(
        { type: 'INIT', sessionToken: sessionToken },
        SDK_ORIGIN
      );
    });

    container.appendChild(iframe);

    return {
      close: function close() {
        if (iframe.parentNode) {
          iframe.parentNode.removeChild(iframe);
        }
      }
    };
  }

  window.OurChatSDK = { open: open };
})();

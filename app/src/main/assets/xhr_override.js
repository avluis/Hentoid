if (typeof this.origOpen === 'undefined') {
   XMLHttpRequest.prototype.origOpen = XMLHttpRequest.prototype.open;
   XMLHttpRequest.prototype.open = function(method, url, async, user, password) {
       this.recordedMethod = method;
       this.recordedUrl = url;
       this.origOpen(method, url, async, user, password);
   };
   XMLHttpRequest.prototype.origSend = XMLHttpRequest.prototype.send;
   XMLHttpRequest.prototype.send = function(body) {
       /* console.log("xhr called with args:", this.recordedMethod, this.recordedUrl, body); */
       xhrHandler.onXhrCall(this.recordedMethod, this.recordedUrl, body);
       this.origSend(body);
   };
}
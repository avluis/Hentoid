localStorage.setItem('ignoring_adblock', '1');

var yourDate = new Date();
var offset = yourDate.getTimezoneOffset();
yourDate = new Date(yourDate.getTime() + (offset*60*1000));
var dateFormatted = yourDate.toISOString().split('T')[0];
sessionStorage.setItem(dateFormatted, '1');
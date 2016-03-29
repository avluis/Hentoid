if(!Object.keys){Object.keys=(
function(){'use strict';var hasOwnProperty=Object.prototype.hasOwnProperty,hasDontEnumBug=!({toString:null}).propertyIsEnumerable('toString'),dontEnums=['toString','toLocaleString','valueOf','hasOwnProperty','isPrototypeOf','propertyIsEnumerable','constructor'],dontEnumsLength=dontEnums.length;return function(obj){if(typeof obj!=='object'&&(typeof obj!=='function'||obj===null)){throw new TypeError('Object.keys called on non-object');}

var result=[],prop,i;for(prop in obj){if(hasOwnProperty.call(obj,prop)){result.push(prop);}}

if(hasDontEnumBug){for(i=0;i<dontEnumsLength;i++){if(hasOwnProperty.call(obj,dontEnums[i])){result.push(dontEnums[i]);}}}
    return result;};}());}

function is_english(){var userLang=navigator.language||navigator.userLanguage;userLang=userLang.toLowerCase();return/^en/.test(userLang);}
function is_asian(){var userLang=navigator.language||navigator.userLanguage;userLang=userLang.toLowerCase();return/^(?:ko|ja)/.test(userLang);}
function random_index(arr){return Math.floor(Math.random()*arr.length);}
function random_property(obj){var result;var count=0;for(var prop in obj)

if(Math.random()<1/++count)
result=prop;return result;}
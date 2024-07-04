(() => {
    let oldPushState = history.pushState;
    history.pushState = function pushState() {
        let ret = oldPushState.apply(this, arguments);
        window.dispatchEvent(new Event('pushstate'));
        window.dispatchEvent(new Event('locationchange'));
        return ret;
    };

    let oldReplaceState = history.replaceState;
    history.replaceState = function replaceState() {
        let ret = oldReplaceState.apply(this, arguments);
        window.dispatchEvent(new Event('replacestate'));
        window.dispatchEvent(new Event('locationchange'));
        return ret;
    };

    window.addEventListener('popstate', () => {
        window.dispatchEvent(new Event('locationchange'));
    });
})();

window.addEventListener("locationchange", function () {
    aaa();
});

document.addEventListener("DOMContentLoaded", (event) => {
    aaa();
});

function cleanHead() {
    clean("#reader-nav");
    clean(".subnav");
}

function clean(selector) {
    var elt = document.querySelector(selector);
    if (elt != null) elt.remove();
}

function eval() {
  return new Promise((resolve) => {
    setTimeout(() => {
        var found = false;
        var container = document.querySelector("#pic_container");
        if (container != null) {
            container.childNodes.forEach((element) => {
                var displayed = true;
                var style = element.style;
                if (style != null) {
                    var display = style.display;
                    if (display != null && display == "none") displayed = false;
                }
                if (!found && displayed) {
                    if ("IMG" == element.nodeName) {
                        console.info("resolve OK IMG");
                        found = true;
                        $interface.$fun(document.URL, document.querySelector("html").innerHTML);
                    } else {
                        var canvas = element.querySelector("canvas");
                        if (canvas != null) {
                            console.info("resolve OK CANVAS");
                            found = true;

                            if ($force_page) {
                                console.info("force page");
                                $interface.$fun(document.URL, document.querySelector("html").innerHTML);
                            } else {
                                cleanHead();
                                /* Delay for redrawing page */
                                setTimeout(() => {
                                    screencap.onLoaded(canvas.width, canvas.height);
                                }, 500);
                            }
                        }
                    }
                }
            });
            if (!found) console.info("resolve KO");
            resolve(found);
        }
    }, 750);
  });
}

async function aaa() {
    console.info("ready");
    var result = await eval();
    if (!result) result = await eval();
    if (!result) result = await eval();
    if (!result) result = await eval();
    if (!result) result = await eval();
    if (!result) result = await eval();
    if (!result) result = await eval();
}
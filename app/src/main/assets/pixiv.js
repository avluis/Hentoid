document.addEventListener("DOMContentLoaded", function () {
    addCustomCss();

    if (document.URL.includes(".php")) {
        markBooksPhp()

    } else {
        markBooks();
    }

});

function addCustomCss() {
    var customCss = document.createElement("style");

    customCss.setAttribute("type", "text/css");
    customCss.textContent = pixivJsInterface.getPixivCustomCss();

    document.head.append(customCss);
}

function markBooks() {
    var observerTarget = document.querySelector("div#wrapper");
    var observer = new MutationObserver((mutations) => {
        for (let i of mutations) {
            var target = i.addedNodes[0];

            if (typeof (target) != 'undefined') {
                if (target.nodeType == 1) {
                    targetClassAttr = target.getAttribute("class");
                    if ((targetClassAttr == "thumb") || (targetClassAttr == "thumbnail-link")) {
                        var markTarget = target.closest("a[href]");
                        var targetBookId = markTarget.getAttribute("href").replace("/en", '').replace("/a", "a").concat("/");
                        var result = pixivJsInterface.isMarkable(targetBookId);
                        if (result == 1) {
                            markTarget.classList.add('watermarked');
                        } else if (result == 2) {
                            markTarget.classList.add("watermarked-merged");
                        }
                    } else if ((targetClassAttr == "works-item-illust works-item grid no-padding")) {
                        var markTarget = target.firstChild;
                        if (pixivJsInterface.isMarkable(markTarget.getAttribute("href"))) {
                            markTarget.classList.add('watermarked');
                        }
                    }
                }
            }
        }
    });

    var option = {
        childList: true,
        subtree: true
    };

    observer.observe(observerTarget, option);
}

function markBooksPhp() {
    var markTargets = document.querySelectorAll(".imgbox");

    for (let markTarget of markTargets) {
        var targetBookId = markTarget.firstChild.getAttribute("href").replace("/en", '').replace("/a", "a").concat("/");
        var result = pixivJsInterface.isMarkable(targetBookId);
        if (result == 1) {
            markTarget.classList.add('watermarked');
        } else if (result == 2) {
            markTarget.classList.add("watermarked-merged");
        }
    }
}
document.addEventListener("DOMContentLoaded", function () {
    var customCss = document.createElement("style");

    customCss.setAttribute("type", "text/css");
    customCss.textContent = pixivJsInterface.getPixivCustomCss();

    document.head.append(customCss);
});
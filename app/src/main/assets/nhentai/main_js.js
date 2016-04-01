(function() {
    "use strict";
    var e = window.nhentai = {};
    e.init = function() {
        for (var e in this) this.hasOwnProperty(e) && /^(install|patch)_/.test(e) && this[e]()
    }, e.install_analytics = function() {
        window.GoogleAnalyticsObject = "ga", window.ga = function() {
            (window.ga.q = window.ga.q || []).push(arguments)
        }, window.ga.l = +(new Date), e.analytics = function() {};
        var t = this;
        e.inject_script("//www.google-analytics.com/analytics.js", {
            async: !0
        }, function() {
            t.analytics = window.ga || function() {}
        }), ga("create", "UA-52345677-1", {
            sampleRate: 1
        }), ga("send", "pageview")
    }, e.install_body_class = function() {
        document.querySelector("body").classList.add("js")
    }, "use strict", e.bind = function() {
        return typeof arguments[2] == "string" ? e.bind_dynamic.apply(null, arguments) : e.bind_regular.apply(null, arguments)
    }, e.bind_dynamic = function(t, n, r, i) {
        return e.bind_regular(t, r, function(t) {
            var r = t.target;
            while (r.parentNode && !e.matches(r, n)) r = r.parentNode;
            if (e.matches(r, n)) return i.apply(r, arguments)
        }, !0)
    }, e.bind_regular = function(e, t, n, r) {
        typeof e == "string" ? e = document.querySelectorAll(e) : !(e instanceof NodeList) && e.constructor !== Array && (e = [e]);
        for (var i = 0; i < e.length; i++) {
            var s = e[i],
                o = !0,
                u = !1,
                a = undefined;
            try {
                for (var f = t.split(/\s+/)[Symbol.iterator](), l; !(o = (l = f.next()).done); o = !0) {
                    var c = l.value;
                    s.addEventListener(c, n, !!r)
                }
            } catch (h) {
                u = !0, a = h
            } finally {
                try {
                    !o && f["return"] && f["return"]()
                } finally {
                    if (u) throw a
                }
            }
        }
    }, e.find_property = function(e, t) {
        for (var n in e)
            if (e.hasOwnProperty(n) && t.test(n)) return e[n]
    }, e.matches = function(t, n) {
        var r = e.find_property(t, /^matches|matchesSelector|(webkit|moz|ms)MatchesSelector$/);
        if (r) return r.call(t, n);
        var i = document.querySelectorAll(n);
        for (var s = 0; s < i.length; s++)
            if (i[s] === t) return !0;
        return !1
    }, e.format = function(e) {
        var t = e.toString(),
            n = typeof arguments[1],
            r = n === "string" || n === "number" ? [].slice.call(arguments, 1) : arguments[1];
        for (var i in r) t = t.replace(RegExp("\\{" + i + "\\}", "gi"), r[i]);
        return t
    }, e.render_template = function(e, t) {
        var n = "var p=[];with(context){p.push('" + e.replace(/[\r\t\n]/g, " ").split("<%").join("	").replace(/((^|%>)[^\t]*)'/g, "$1\r").replace(/\t=(.*?)%>/g, "',$1,'").split("	").join("');").split("%>").join("p.push('").split("\r").join("\\'") + "');}return p.join('');";
        return (new Function("context", n))(t)
    }, e.intervalometer = function(e, t, n, r) {
        var i = 0,
            s = setInterval(function() {
                i++, (e() || n && i > n || i * t > r) && clearInterval(s)
            }, t);
        return s
    }, e.forEach = function(e, t) {
        for (var n = 0; n < e.length; n++) t(e[n])
    }, e.clamp = function(e, t, n) {
        return Math.max(t, Math.min(e, n))
    }, e.extend = function(e, t) {
        for (var n in t) t.hasOwnProperty(n) && (e[n] = t[n])
    }, e.inject_script = function(t, n, r) {
        var i = document.createElement("script");
        e.extend(i, n), t.indexOf("//") === 0 && (t = document.location.protocol + t), i.src = t;
        var s = document.getElementsByTagName("script")[0];
        s.parentNode.insertBefore(i, s), r && e.bind(i, "load", r)
    }, e.rate_limit = function(e, t) {
        var n = null;
        return function() {
            var r = n;
            n = Date.now();
            if (Date.now() - r < t) return;
            return e.apply(this, arguments)
        }
    }, e.sgn = function(e) {
        return e >= 0 ? 1 : -1
    }, "use strict", e.browser = {}, e.browser.supports_translate = function() {
        var e = document.createElement("div");
        e.style.position = "absolute", e.style.zIndex = -100;
        var t = !1,
            n = {
                WebkitTransform: "-webkit-transform",
                OTransform: "-o-transform",
                MSTransform: "-ms-transform",
                MozTransform: "-moz-transform",
                Transform: "transform"
            };
        document.body.insertBefore(e, null);
        for (var r in n)
            if (e.style[r] !== undefined) {
                e.style[r] = "translate3d(1px, 1px, 1px)";
                var i = window.getComputedStyle(e).getPropertyValue(n[r]);
                if (i !== undefined && i.length > 0 && i !== "none") {
                    t = !0;
                    break
                }
            }
        return document.body.removeChild(e), t
    }(), "use strict", e.cookies = {}, e.cookies.get = function(e, t) {
        var n = document.cookie.split(/;\s*/);
        for (var r = 0; r < n.length; r++)
            if (n[r].indexOf(e + "=") === 0) return n[r].split("=", 1)[1];
        return t
    }, e.cookies.has = function(t) {
        return e.cookies.get(t, !1) !== !1
    }, e.cookies.set = function(e, t, n) {
        var r = [e + "=" + t];
        if (n) {
            var i = new Date;
            i.setTime(i.getTime() + n * 24 * 60 * 60 * 1e3), r.push("expires=" + i.toUTCString())
        }
        r.push("path=/"), document.cookie = r.join("; ")
    }, e.cookies["delete"] = function(t) {
        return e.cookies.set(t, "", -1)
    }, "use strict", e.storage = {}, e.storage.set = function(e, t) {
        window.sessionStorage.setItem(e, JSON.stringify(t))
    }, e.storage.get = function(e, t) {
        return window.sessionStorage.getItem(e) === null ? t : JSON.parse(window.sessionStorage.getItem(e))
    }, "use strict", e.advertising = function(e) {
        this.is_mobile = "ontouchstart" in window || window.DocumentTouch && document instanceof DocumentTouch, this.country = e, this.popads_counter = 0
    }, e.advertising.prototype.show_popunder = function() {
        return;
    }, "use strict", e.reader = function(t) {
        e.extend(this, t), this.num_pages = this.gallery.images.pages.length, this.gallery_url = e.format("/g/{0}/", this.gallery.id), this.direction = 1, this.$image = document.querySelector("#page-container img"), this.$image_container = document.querySelector("#image-container"), this.install_scroller(), this.install_touch();
        var n = this;
        e.bind(this.$image, "load", function() {
            n.direction === 1 ? n.preload(n.current_page - 1, n.current_page + 3) : n.preload(n.current_page - 3, n.current_page + 1)
        }), e.bind(window, "popstate", function(e) {
            e.state && e.state.page && n.set_page(e.state.page, !1)
        }), e.bind("#page-container", "a", "click", function(e) {
            var t = this.href.match(new RegExp("/g/\\d+/(\\d+)"));
            t && (e.preventDefault(), n.set_page(parseInt(t[1], 10), !0))
        })
    }, e.reader.prototype.get_page_url = function(t) {
        return e.
        format("/g/{0}/{1}/", this.gallery.id, t)
    }, e.reader.prototype.get_extension = function(e) {
        return {
            j: "jpg",
            p: "png",
            g: "gif"
        }[this.gallery.images.pages[e - 1].t]
    }, e.reader.prototype.get_image_url = function(t) {
        return e.format("{0}galleries/{1}/{2}.{3}", this.media_url, this.gallery.media_id, t, this.get_extension(t))
    }, e.reader.prototype.get_thumbnail_url = function(t) {
        return e.format("{0}galleries/{1}/{2}.{3}", this.media_url, this.gallery.media_id, t + "t", this.get_extension(t))
    }, e.reader.prototype.preload_page = function(e) {
        (new Image).src = this.get_image_url(e)
    }, e.reader.prototype.preload = e.rate_limit(function(e, t) {
        console.debug("Preloading pages", e, "to", t);
        for (var n = Math.max(1, e); n < Math.min(t, this.num_pages); n++) this.preload_page(n)
    }, 100), e.reader.prototype.stop_load = function() {
        return window.stop ? function() {
            window.stop()
        } : document.execCommand ? function() {
            document.execCommand("Stop", !1)
        } : function() {}
    }(), e.reader.prototype.set_page = function(t, n) {
        if (t === this.current_page) return;
        this.stop_load(), console.debug("Switching to page", t), this.current_page = t, this.$image.src = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", this.$image.src = this.get_image_url(t), this.$image.width = this.gallery.images.pages[t - 1].w, this.$image.height = this.gallery.images.pages[t - 1].h, this.$image_container.querySelector("a").href = t < this.num_pages ? this.get_page_url(t + 1) : this.gallery_url;
        var r = this.render_pagination(t);
        e.forEach(document.querySelectorAll("#page-container .pagination"), function(e) {
            e.innerHTML = r
        }), document.title = document.title.replace(/Page (\d+) \u00bb/, e.format("Page {0} \u00bb", t)), n && (history.pushState({
            page: t
        }, document.title, this.get_page_url(t)), e.analytics("send", "pageview", window.location.pathname)), document.querySelector("#page-container").scrollIntoView()
    }, e.reader.prototype.previous_page = function() {
        this.direction = -1, this.set_page(Math.max(1, this.current_page - 1), !0)
    }, e.reader.prototype.next_page = function() {
        this.direction = 1, this.set_page(Math.min(this.current_page + 1, this.num_pages), !0)
    }, e.reader.prototype.render_pagination = function(t) {
        return e.render_template('\n		<a href="<%= r.get_page_url(1) %>" class="first"><i class="fa fa-chevron-left"></i><i class="fa fa-chevron-left"></i></a>\n\n		<% if (page > 1) {%>\n			<a href="<%= r.get_page_url(page - 1) %>" class="previous"><i class="fa fa-chevron-left"></i></a>\n		<%} else {%>\n			<a class="previous invisible"><i class="fa fa-chevron-left"></i></a>\n		<%} %>\n\n		<span class="page-number"><span class="current"><%= page %></span> <span class="divider">of</span> <span class="num-pages"><%= r.num_pages %></span></span>\n\n		<% if (page < r.num_pages) {%>\n			<a href="<%= r.get_page_url(page + 1) %>" class="next"><i class="fa fa-chevron-right"></i></a>\n		<%} else {%>\n			<a class="next invisible"><i class="fa fa-chevron-right"></i></a>\n		<%} %>\n\n		<a href="<%= r.get_page_url(r.num_pages) %>" class="last"><i class="fa fa-chevron-right"></i><i class="fa fa-chevron-right"></i></a>\n	', {
            r: this,
            page: t
        })
    }, e.reader.prototype.install_scroller = function() {
        var t = this,
            n = null;
        e.bind(document, "keydown", function(e) {
            if (e.target.tagName.toLowerCase() === "input" || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
            n && (clearInterval(n), n = null);
            var r = !0;
            e.which === 83 || e.which === 40 ? n = setInterval(function() {
                window.scrollBy(0, 5)
            }, 5) : e.which === 87 || e.which === 38 ? n = setInterval(function() {
                window.scrollBy(0, -5)
            }, 5) : e.which === 37 || e.which === 65 ? t.previous_page() : e.which === 39 || e.which === 68 ? t.next_page() : r = !1, r && e.preventDefault()
        }), e.bind(document, "keyup visibilitychange", function() {
            n && (clearInterval(n), n = null)
        })
    }, e.reader.prototype.install_touch = function() {
        var t = this,
            n = 100,
            r = !1,
            i = {
                x: 0,
                y: 0
            },
            s = {
                x: 0,
                y: 0
            };
        e.bind(this.$image_container, "touchstart", function(e) {
            r = !1, i.x = e.targetTouches[0].pageX, i.y = e.targetTouches[0].pageY
        }), e.bind(this.$image_container, "touchmove", function(o) {
            s.x = o.targetTouches[0].pageX, s.y = o.targetTouches[0].pageY, Math.abs(i.x - s.x) > 10 && (r = !0);
            if (!r) return;
            o.stopPropagation();
            var u = s.x - i.x;
            t.current_page === 1 && u > 0 || t.current_page === t.num_pages && u < 0 ? u = e.sgn(u) * Math.pow(Math.abs(u), .65) : u = e.clamp(u, -n, n), e.browser.supports_transform ? (t.$image.style.transform = "translate3d(" + u + "px, 0px, 0px)", t.$image.style.transition = "0s") : t.$image.style.left = u + "px"
        }), e.bind(this.$image_container, "touchend", function(o) {
            r = !1, e.browser.supports_transform ? (t.$image.style.transform = "translate3d(0px, 0px, 0px)", t.$image.style.transition = "0.3s") : t.$image.style.left = "0px";
            var u = i.x - s.x;
            u > n ? t.next_page() : u < -n && t.previous_page()
        }), e.bind(this.$image_container, "touchcancel", function(e) {
            r = !1, this.dispatchEvent(new CustomEvent("touchend"))
        })
    }, "use strict", e.install_theme_previewer = function() {
        var t = document.querySelector("html"),
            n = document.querySelector("#id_theme");
        if (!n) return;
        e.bind(n, "change", function() {
            var e = t.classList;
            for (var n = 0; n < e.length; n++) e[n].indexOf("theme-") === 0 && e.remove(e[n]);
            e.add("theme-" + this.value.toLowerCase())
        })
    }, "use strict", e.lazy_loader = function(e) {
        this.$elements = [].slice.call(e), this.interval = setInterval(this.show_images.bind(this), 100);
        for (var t = 0; t < this.$elements.length; t++) {
            var n = e[t];
            n.src = this.placeholder(+n.getAttribute("width"), +n.getAttribute("height"))
        }
        this.show_images()
    }, e.lazy_loader.prototype.placeholder = function() {
        var e = {};
        if (window.CanvasRenderingContext2D) {
            var t = document.createElement("canvas");
            return function(n, r) {
                var i = n + "|" + r;
                return i in e ? e[i] : (t.width !== n && (t.width = n), t.height !== r && (t.height = r), e[i] = t.toDataURL())
            }
        }
        return function(e, t) {
            return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMgAAAETCAQAAADZ34FNAAAAgUlEQVR4Ae3BMQEAAADCIPunXgsvYAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADAU67SAAGCHA2qAAAAAElFTkSuQmCC"
        }
    }(), e.lazy_loader.prototype.in_viewport = function(e, t) {
        var n = e.getBoundingClientRect();
        return n.bottom >= -t && n.top <= window.innerHeight + t
    }, e.lazy_loader.prototype.show_images = function() {
        if (!this.$elements.length) {
            clearInterval(this.interval);
            return
        }
        var e = !1,
            t = window.innerHeight / 2;
        for (var n = this.$elements.length - 1; n >= 0; n--) {
            var r = this.$elements[n];
            if (!this.in_viewport(r, t)) {
                if (!e) continue;
                break
            }
            e = !0, this.$elements.splice(n, 1), r.src = r.getAttribute("data-src")
        }
    }, "use strict", e.relative_time = function(t) {
        var n = new Date,
            t = new Date(t),
            r = (n - t) / 1e3,
            i = [];
        i.push([r, "second"]), i.push([r / 60, "minute"]), i.push([r / 3600 % 3600, "hour"]), i.push([r / 86400 % 86400, "day"]), i.push([r / 604800 % 604800, "week"]), i.push([12 * (n.getFullYear() - t.getFullYear()) + (n.getMonth() - t.getMonth()), "month"]);
        var s = n.getFullYear() - t.getFullYear();
        (n.getMonth() < t.getMonth() || n.getMonth() == t.getMonth() && n.getDate() < t.getDate()) && s--, i.push([s, "year"]);
        for (var o = 0; o < i.length; o++) i[o][0] = Math.floor(i[o][0]) || Number.POSITIVE_INFINITY;
        i.sort(function(e, t) {
            return e[0] - t[0]
        });
        var u = i[0];
        return e.format("{0} {1}{2} ago", u[0], u[1], u[0] === 1 ? "" : "s")
    }, e.update_times = function() {
        var t = 6e4,
            n = document.querySelectorAll("time[datetime]");
        for (var r = 0; r < n.length; r++) {
            var i = e.relative_time(n[r].getAttribute("datetime"));
            n[r].innerHTML !== i && (n[r].innerHTML = i), i.indexOf("second") !== -1 && (t = 1e3)
        }
        return t
    }, e.install_relative_time = function() {
        var t = function n() {
            var t = e.update_times();
            setTimeout(n, t)
        };
        t()
    }
}).call(this);
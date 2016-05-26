(function () {
    "use strict";

    function t(e) {
        this.length = 0, this.offset = 0, this.array = new Uint8Array(e), this.view = new DataView(this.array.buffer, 0, this.length)
    }

    function n(e) {
        e--;
        for (var t = 0; t < 6; t++) e |= e >> (1 << t);
        return e + 1
    }

    function r(e, t, n, r, i) {
        var s = !1;
        if (s) e.set(s.call(t, n, r), i);
        else
            for (var o = n; o < r; o++) e[i + o] = t[o]
    }
    var e = window.N = {};
    e.init = function () {
        var t = arguments.length <= 0 || arguments[0] === undefined ? {} : arguments[0];
        for (var n in this) this.hasOwnProperty(n) && /^(install|patch)_/.test(n) && this[n]();
        e.options = t
    }, e.install_analytics = function () {
        window.GoogleAnalyticsObject = "ga", e.analytics = window.ga = window.ga || function () {
            (window.ga.q = window.ga.q || []).push(arguments)
        }, window.ga.l = +(new Date), e.inject_script("https://www.google-analytics.com/analytics.js"), e.analytics("create", "UA-52345677-1", {
            sampleRate: 1
        }), e.analytics("send", "pageview")
    }, e.patch_safari_touch_hover = function () {
        e.bind(document, "touchstart", function () {}, !0)
    }, e.install_sentry = function () {
        Raven.config("https://b42380b6f72e4650ac4607e6ba15934c@app.getsentry.com/52175").install()
    }, "use strict", e.key = {
        BACKSPACE: 8,
        TAB: 9,
        ENTER: 13,
        SHIFT: 16,
        CTRL: 17,
        ALT: 18,
        ESCAPE: 27,
        SPACEBAR: 32,
        LEFT_ARROW: 37,
        UP_ARROW: 38,
        RIGHT_ARROW: 39,
        DOWN_ARROW: 40,
        A: 65,
        D: 68,
        S: 83,
        W: 87
    }, "use strict", e.cookies = {}, e.cookies.get = function (e, t) {
        var n = document.cookie.split(/;\s*/);
        for (var r = 0; r < n.length; r++) {
            var i = n[r];
            if (i.indexOf(e + "=") === 0) return i.split("=", 1)[1]
        }
        return t
    }, e.cookies.has = function (t) {
        return e.cookies.get(t, !1) !== !1
    }, e.cookies.set = function (e, t, n) {
        var r = [e + "=" + t];
        if (n) {
            var i = new Date;
            i.setTime(i.getTime() + n * 1e3), r.push("expires=" + i.toUTCString())
        }
        r.push("path=/"), document.cookie = r.join("; ")
    }, e.cookies["delete"] = function (t) {
        return e.cookies.set(t, "", -1)
    }, "use strict", e.bind = function () {
        return typeof arguments[2] == "string" ? e.bind_dynamic.apply(null, arguments) : e.bind_regular.apply(null, arguments)
    }, e.bind_dynamic = function (t, n, r, i) {
        var s = function (r) {
            var s = e.find_parent(r.target, n);
            if (s) return i.apply(s, arguments)
        };
        return e.bind_regular(t, r, s, !0), s
    }, e.bind_regular = function (e, t, n, r) {
        typeof e == "string" ? e = document.querySelectorAll(e) : !(e instanceof NodeList) && e.constructor !== Array && (e = [e]);
        var t = t.split(/\s+/);
        for (var i = 0; i < e.length; i++) {
            var s = e[i];
            for (var o = 0; o < t.length; o++) s.addEventListener(t[o], n, !!r)
        }
    }, e.unbind = function (e, t, n, r) {
        typeof e == "string" ? e = document.querySelectorAll(e) : !(e instanceof NodeList) && e.constructor !== Array && (e = [e]);
        var t = t.split(/\s+/);
        for (var i = 0; i < e.length; i++) {
            var s = e[i];
            for (var o = 0; o < t.length; o++) s.removeEventListener(t[o], n, !!r)
        }
    }, e.find_parent = function (t, n) {
        while (!e.matches(t, n) && t.parentNode) t = t.parentNode;
        if (e.matches(t, n)) return t
    }, e.matches = function (e, t) {
        var n = e.matches || e.matchesSelector || e.webkitMatchesSelector || e.mozMatchesSelector || e.msMatchesSelector;
        if (n) return n.call(e, t);
        var r = document.querySelectorAll(t);
        for (var i = 0; i < r.length; i++)
            if (r[i] === e) return !0;
        return !1
    }, e.format = function (e) {
        var t = typeof arguments[1],
            n = t === "string" || t === "number" ? [].slice.call(arguments, 1) : arguments[1];
        for (var r = 0; r < n.length; r++) e = e.replace(RegExp("\\{" + r + "\\}", "gi"), n[r]);
        return e
    }, e.pad = function (e, t, n) {
        var r = e;
        for (var i = 0; i < t - r.length; i++) r = n + r;
        return r
    }, e.html_escape = function (e) {
        var t = {
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            '"': "&38;",
            "'": "&#39;"
        };
        return e.toString().replace(/[&<>"']/g, function (e) {
            return t[e]
        })
    }, e.render_template = function (t) {
        var n = arguments.length <= 1 || arguments[1] === undefined ? {} : arguments[1],
            r = "\n		var parts = [];\n\n		with (context) {\n			parts.push('" + t.replace(/[\r\t\n]/g, " ").split("<%").join("	").replace(/((^|%>)[^\t]*)'/g, "$1\r").replace(/\t!=(.*?)%>/g, "',$1,'").replace(/\t=(.*?)%>/g, "',N.html_escape($1),'").split("	").join("');").split("%>").join("parts.push('").split("\r").join("\\'") + "');\n		}\n\n		return parts.join('');";
        return n.N = e, (new Function("context", r))(n)
    }, e.create_element = function (t) {
        var n = arguments.length <= 1 || arguments[1] === undefined ? {} : arguments[1],
            r = document.createElement("div");
        return r.innerHTML = e.render_template(t, n), r.children[0]
    }, e.pretty_integer = function (e) {
        return e.toString().replace(/\B(?=(?:\d{3})+(?!\d))/g, ",")
    }, e.strip = function (e) {
        return e.replace(/^\s+|\s+$/g, "")
    }, e.forEach = function (e, t) {
        for (var n = 0; n < e.length; n++) t(e[n])
    }, e.clamp = function (e, t, n) {
        return Math.max(t, Math.min(e, n))
    }, e.extend = function (e, t, n) {
        for (var r in t)
            if (t.hasOwnProperty(r)) {
                if (n && e.hasOwnProperty(r)) continue;
                e[r] = t[r]
            }
    }, e.keys = function (e) {
        if (Object.keys) return Object.keys(e);
        var t = [];
        for (var n in e) e.hasOwnProperty(n) && t.push(n);
        return t
    }, e.inject_script = function (t, n, r) {
        var i = document.createElement("script");
        e.extend(i, n), t.indexOf("//") === 0 && (t = document.location.protocol + t), i.src = t;
        var s = document.getElementsByTagName("script")[0];
        s.parentNode.insertBefore(i, s), r && e.bind(i, "load", r)
    }, e.rate_limit = function (e, t) {
        var n = null;
        return function () {
            var r = n;
            n = Date.now();
            if (Date.now() - r < t) return;
            return e.apply(this, arguments)
        }
    }, e.sgn = function (e) {
        return e >= 0 ? 1 : -1
    }, e.debouncer = function (e) {
        this.delay = e, this.last_hit = null
    }, e.debouncer.prototype.hit = function () {
        this.is_ready() && (this.last_hit = +(new Date))
    }, e.debouncer.prototype.is_ready = function () {
        return this.last_hit ? +(new Date) - this.last_hit > this.delay : !0
    }, "use strict", e.encode_params = function (t) {
        var n = [],
            r = e.keys(t),
            i = !0,
            s = !1,
            o = undefined;
        try {
            for (var u = r.length[Symbol.iterator](), a; !(i = (a = u.next()).done); i = !0) {
                var f = a.value;
                n.push(e.format("{0}={1}", encodeURIComponent(f), encodeURIComponent(t[f])))
            }
        } catch (l) {
            s = !0, o = l
        } finally {
            try {
                !i && u["return"] && u["return"]()
            } finally {
                if (s) throw o
            }
        }
        return n.join("&")
    }, e.http = function (t) {
        var n = {
            headers: {
                "X-CSRFToken": e.options.csrf_token,
                "X-Requested-With": "XMLHttpRequest"
            },
            method: "GET",
            success: function () {},
            failure: function () {},
            xhr_properties: {},
            params: null,
            json: null
        };
        e.extend(t, n, !0);
        var r = new XMLHttpRequest;
        r.open(t.method, t.url, !0);
        var i = e.keys(t.headers),
            s = !0,
            o = !1,
            u = undefined;
        try {
            for (var a = i[Symbol.iterator](), f; !(s = (f = a.next()).done); s = !0) {
                var l = f.value;
                r.setRequestHeader(l, t.headers[l])
            }
        } catch (c) {
            o = !0, u = c
        } finally {
            try {
                !s && a["return"] && a["return"]()
            } finally {
                if (o) throw u
            }
        }
        e.bind(r, "load", t.success), e.bind(r, "error", t.failure);
        var h = null;
        return t.params !== null ? (h = e.encode_params(t.params), r.setRequestHeader("Content-Type", "application/x-www-form-urlencoded")) : t.json !== null && (h = JSON.stringify(t.json), r.setRequestHeader("Content-Type", "application/json")), e.extend(r, t.xhr_properties), r.send(h), r
    }, "use strict", e.browser = {}, e.browser.supports_transform = function () {
        var t = document.createElement("div");
        t.style.position = "absolute", t.style.zIndex = -100, document.body.insertBefore(t, null);
        var n = !1,
            r = {
                WebkitTransform: "-webkit-transform",
                OTransform: "-o-transform",
                MSTransform: "-ms-transform",
                MozTransform: "-moz-transform",
                Transform: "transform"
            },
            i = e.keys(r);
        for (var s = 0; s < i.length; s++) {
            var o = i[s];
            if (t.style[o] !== undefined) {
                t.style[o] = "translate3d(1px, 1px, 1px)";
                var u = window.getComputedStyle(t).getPropertyValue(r[o]);
                if (u && u.length > 0 && u !== "none") {
                    n = !0;
                    break
                }
            }
        }
        return document.body.removeChild(t), n
    }(), e.browser.is_safari = !!navigator.userAgent.match(/Version\/[\d\.]+.*Safari/), "use strict", e.session_storage = {}, e.session_storage.set = function (e, t) {
        sessionStorage.setItem(e, JSON.stringify(t))
    }, e.session_storage.has = function (e) {
        return sessionStorage.getItem(e) !== null
    }, e.session_storage.get = function (t, n) {
        return e.session_storage.has(t) ? JSON.parse(sessionStorage.getItem(t)) : n
    }, e.session_storage["delete"] = function (e) {
        sessionStorage.removeItem(e)
    }, e.cookie_storage = {}, e.cookie_storage.set = function (t, n) {
        e.cookies.set(t, JSON.stringify(n), 28800)
    }, e.cookie_storage.has = function (t) {
        return e.cookies.has(t)
    }, e.cookie_storage.get = function (t, n) {
        return e.cookie_storage.has(t) ? JSON.parse(e.cookies.get(t)) : n
    }, e.cookie_storage["delete"] = function (t) {
        e.cookies["delete"](t)
    }, e.storage = function () {
        if (window.sessionStorage) try {
            return sessionStorage.setItem("_storage_test", 1), sessionStorage.removeItem("_storage_test"), e.session_storage
        } catch (t) {
            console.log("Your browser does not support sessionStorage! Falling back to cookies...")
        }
        return e.cookie_storage
    }(), "use strict", e.advertising = function (e) {
        this.is_mobile = "ontouchstart" in window || window.DocumentTouch && document instanceof DocumentTouch, this.country = e, this.popads_counter = 0
    }, "use strict", e.install_theme_previewer = function () {
        var t = document.querySelector("html"),
            n = document.querySelector("#id_theme");
        if (!n) return;
        e.bind(n, "change", function () {
            var e = t.classList;
            for (var n = 0; n < e.length; n++) e.remove(e[n]);
            e.add("theme-" + this.value.toLowerCase())
        })
    }, "use strict", e.lazy_loader = function (e) {
        this.$elements = [].slice.call(e), this.$elements.reverse(), this.interval = setInterval(this.show_images.bind(this), 100);
        var t = !0,
            n = !1,
            r = undefined;
        try {
            for (var i = this.$elements[Symbol.iterator](), s; !(t = (s = i.next()).done); t = !0) {
                var o = s.value;
                o.src = this.placeholder(+o.getAttribute("width"), +o.getAttribute("height"))
            }
        } catch (u) {
            n = !0, r = u
        } finally {
            try {
                !t && i["return"] && i["return"]()
            } finally {
                if (n) throw r
            }
        }
        this.show_images()
    }, e.lazy_loader.prototype.placeholder = function () {
        var e = {};
        if (window.CanvasRenderingContext2D) {
            var t = document.createElement("canvas");
            return function (n, r) {
                var i = n + "|" + r;
                return i in e ? e[i] : (t.width !== n && (t.width = n), t.height !== r && (t.height = r), e[i] = t.toDataURL())
            }
        }
        return function (e, t) {
            return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMgAAAETCAQAAADZ34FNAAAAgUlEQVR4Ae3BMQEAAADCIPunXgsvYAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADAU67SAAGCHA2qAAAAAElFTkSuQmCC"
        }
    }(), e.lazy_loader.prototype.in_viewport = function (e, t) {
        var n = e.getBoundingClientRect();
        return n.bottom >= -t && n.top <= window.innerHeight + t
    }, e.lazy_loader.prototype.show_images = function () {
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
    }, "use strict", e.install_frame_buster = function () {
        if (["localhost", "nhentai.net", "127.0.0.1"].indexOf(window.location.hostname) !== -1) return;
        document.body.style = "display: none", window.location = "http://nhentai.net" + window.location.pathname + window.location.search + window.location.hash
    }, "use strict", e.reader = function (t) {
        e.extend(this, t), this.num_pages = this.gallery.images.pages.length, this.gallery_url = e.format("/g/{0}/", this.gallery.id), this.direction = 1, this.click_debouncer = new e.debouncer(500), this.$image = document.querySelector("#page-container img"), this.$image_container = document.querySelector("#image-container"), this.$image.className = "fit-horizontal", this.install_scroller(), this.install_link_catcher(), this.install_image_navigation();
        var n = this;
        e.bind(this.$image, "load", function () {
            n.direction === 1 ? n.preload(n.current_page - 1, n.current_page + 3) : n.preload(n.current_page - 3, n.current_page + 1)
        }), e.bind(window, "popstate", function (e) {
            e.state && e.state.page && n.set_page(e.state.page, !1)
        })
    }, e.reader.prototype.get_page_url = function (t) {
        return e.format("/g/{0}/{1}/", this.gallery.id, t)
    }, e.reader.prototype.get_extension = function (e) {
        return {
            j: "jpg",
            p: "png",
            g: "gif"
        }[this.gallery.images.pages[e - 1].t]
    }, e.reader.prototype.get_image_url = function (t) {
        return e.format("{0}galleries/{1}/{2}.{3}", this.media_url, this.gallery.media_id, t,
            this.get_extension(t))
    }, e.reader.prototype.get_thumbnail_url = function (t) {
        return e.format("{0}galleries/{1}/{2}.{3}", this.media_url, this.gallery.media_id, t + "t", this.get_extension(t))
    }, e.reader.prototype.preload_page = function (e) {
        (new Image).src = this.get_image_url(e)
    }, e.reader.prototype.preload = e.rate_limit(function (e, t) {
        console.debug("Preloading pages", e, "to", t);
        for (var n = Math.max(1, e); n < Math.min(t, this.num_pages); n++) this.preload_page(n)
    }, 100), e.reader.prototype.stop_load = function () {
        return window.stop ? function () {
            window.stop()
        } : document.execCommand ? function () {
            document.execCommand("Stop", !1)
        } : function () {}
    }(), e.reader.prototype.set_page = function (t, n) {
        if (t === this.current_page) return;
        this.stop_load(), console.debug("Switching to page", t), this.current_page = t, this.$image.src = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", this.$image.src = this.get_image_url(t), this.$image.width = this.gallery.images.pages[t - 1].w, this.$image.height = this.gallery.images.pages[t - 1].h, this.$image_container.querySelector("a").href = t < this.num_pages ? this.get_page_url(t + 1) : this.gallery_url;
        var r = this.render_pagination(t);
        e.forEach(document.querySelectorAll("#page-container .pagination"), function (e) {
            e.innerHTML = r
        }), document.title = document.title.replace(/Page (\d+) \u00bb/, e.format("Page {0} \u00bb", t)), n && (history.pushState({
            page: t
        }, document.title, this.get_page_url(t)), e.analytics("send", "pageview", window.location.pathname)), this.$image_container.scrollIntoView()
    }, e.reader.prototype.previous_page = function () {
        this.direction = -1, this.set_page(Math.max(1, this.current_page - 1), !0)
    }, e.reader.prototype.next_page = function () {
        this.direction = 1, this.set_page(Math.min(this.current_page + 1, this.num_pages), !0)
    }, e.reader.prototype.render_pagination = function (t) {
        return e.render_template('\n		<a href="<%= r.get_page_url(1) %>" class="first"><i class="fa fa-chevron-left"></i><i class="fa fa-chevron-left"></i></a>\n\n		<% if (page > 1) { %>\n				<a href="<%= r.get_page_url(page - 1) %>" class="previous"><i class="fa fa-chevron-left"></i></a>\n		<% } else { %>\n				<a class="previous invisible"><i class="fa fa-chevron-left"></i></a>\n		<% } %>\n\n		<span class="page-number"><span class="current"><%= page %></span> <span class="divider">of</span> <span class="num-pages"><%= r.num_pages %></span></span>\n\n		<% if (page < r.num_pages) { %>\n				<a href="<%= r.get_page_url(page + 1) %>" class="next"><i class="fa fa-chevron-right"></i></a>\n		<% } else { %>\n				<a class="next invisible"><i class="fa fa-chevron-right"></i></a>\n		<% } %>\n\n		<a href="<%= r.get_page_url(r.num_pages) %>" class="last"><i class="fa fa-chevron-right"></i><i class="fa fa-chevron-right"></i></a>\n	', {
            r: this,
            page: t
        })
    }, e.reader.prototype.install_scroller = function () {
        var t = this,
            n = null;
        e.bind(document, "keydown", function (r) {
            if (r.target.tagName.toLowerCase() === "input" || r.metaKey || r.ctrlKey || r.shiftKey || r.altKey) return;
            n && (clearInterval(n), n = null);
            var i = !0;
            switch (r.which) {
            case e.key.S:
            case e.key.DOWN_ARROW:
                n = setInterval(function () {
                    window.scrollBy(0, 5)
                }, 5);
                break;
            case e.key.W:
            case e.key.UP_ARROW:
                n = setInterval(function () {
                    window.scrollBy(0, -5)
                }, 5);
                break;
            case e.key.A:
            case e.key.LEFT_ARROW:
                t.previous_page();
                break;
            case e.key.D:
            case e.key.RIGHT_ARROW:
                t.next_page();
                break;
            default:
                i = !1
            }
            i && r.preventDefault()
        }), e.bind(document, "keyup visibilitychange", function () {
            n && (clearInterval(n), n = null)
        })
    }, e.reader.prototype.install_link_catcher = function () {
        var t = this;
        e.bind("#page-container", ".pagination a", "click", function (e) {
            var n = this.href.match(new RegExp("/g/\\d+/(\\d+)"));
            n && (e.preventDefault(), t.set_page(parseInt(n[1], 10), !0))
        }, !0)
    }, e.reader.prototype.install_image_navigation = function () {
        var t = this;
        e.bind("#image-container", "img", "click", function (e) {
            e.preventDefault();
            if (!t.click_debouncer.is_ready()) return;
            t.click_debouncer.hit();
            var n = e.offsetX / this.width;
            n < .4 ? (console.log("You tapped left"), t.previous_page()) : (console.log("You tapped right"), t.current_page === t.num_pages ? window.location = t.gallery_url : t.next_page())
        })
    }, "use strict", e.relative_time = function (t) {
        var n = new Date,
            t = new Date(t),
            r = (n - t) / 1e3,
            i = [];
        i.push([r, "second"]), i.push([r / 60, "minute"]), i.push([r / 3600 % 3600, "hour"]), i.push([r / 86400 % 86400, "day"]), i.push([r / 604800 % 604800, "week"]), i.push([12 * (n.getFullYear() - t.getFullYear()) + (n.getMonth() - t.getMonth()), "month"]);
        var s = n.getFullYear() - t.getFullYear();
        (n.getMonth() < t.getMonth() || n.getMonth() == t.getMonth() && n.getDate() < t.getDate()) && s--, i.push([s, "year"]);
        for (var o = 0; o < i.length; o++) i[o][0] = Math.floor(i[o][0]) || Number.POSITIVE_INFINITY;
        i.sort(function (e, t) {
            return e[0] - t[0]
        });
        var u = i[0];
        return e.format("{0} {1}{2} ago", u[0], u[1], u[0] === 1 ? "" : "s")
    }, e.update_times = function () {
        var t = 6e4,
            n = document.querySelectorAll("time[datetime]");
        for (var r = 0; r < n.length; r++) {
            var i = n[r],
                s = e.relative_time(i.getAttribute("datetime"));
            i.innerHTML !== s && (i.innerHTML = s), s.indexOf("second") !== -1 && (t = 1e3)
        }
        return t
    }, e.install_relative_time = function () {
        var t = function n() {
            var t = e.update_times();
            setTimeout(n, t)
        };
        t()
    }, "use strict", e.CRC = {}, e.CRC.table = null, e.CRC.compute_table = function () {
        var e = [];
        for (var t = 0; t < 256; t++) {
            var n = t;
            for (var r = 0; r < 8; r++) n = n & 1 ? 3988292384 ^ n >>> 1 : n >>> 1;
            e[t] = n
        }
        return e
    }, e.CRC.crc32 = function (t) {
        var n = -1,
            r = e.CRC.table || (e.CRC.table = e.CRC.compute_table()),
            i;
        t instanceof ArrayBuffer ? i = t.byteLength : i = t.length;
        for (var s = 0; s < i; s++) n = n >>> 8 ^ r[(n ^ t[s]) & 255];
        return ~n
    }, "use strict", t.prototype.resize_to_fit = function (e) {
        if (e <= this.length) return;
        if (e > this.array.length) {
            console.log("Resizing", this.array.length, "->", e);
            var t = new Uint8Array(n(e));
            t.set(this.array, 0), this.array = t
        }
        this.view = new DataView(this.array.buffer, 0, e), this.length = e
    }, t.prototype.extend = function (e) {
        this.resize_to_fit(this.offset + e.length), r(this.array, e.array, 0, e.length, this.offset), this.offset += e.length
    }, t.prototype.write_array = function (e) {
        this.resize_to_fit(this.offset + e.length), this.array.set(e, this.offset), this.offset += e.length
    }, t.prototype.write_short = function (e) {
        this.resize_to_fit(this.offset + 2), this.view.setUint16(this.offset, e, !0), this.offset += 2
    }, t.prototype.write_int = function (e) {
        this.resize_to_fit(this.offset + 4), this.view.setUint32(this.offset, e, !0), this.offset += 4
    }, t.prototype.write_bytes = function (e) {
        this.resize_to_fit(this.offset + e.length);
        for (var t = 0; t < e.length; t++) this.view.setUint8(this.offset++, e.charCodeAt(t), !0)
    }, t.prototype.to_blob = function (e) {
        try {
            return new Blob([this.view], {
                type: e
            })
        } catch (t) {
            var n = new(window.BlobBuilder || window.WebKitBlobBuilder || window.MozBlobBuilder || window.MSBlobBuilder),
                i = new ArrayBuffer(this.length),
                s = new Uint8Array(i);
            return r(s, this.array, 0, this.length, 0), n.append(i), n.getBlob(e)
        }
    }, e.zipfile = function (e, n) {
        this.has_download_attribute = "download" in document.createElementNS("http://www.w3.org/1999/xhtml", "a");
        if (!(window.Uint8Array && window.URL && URL.createObjectURL && window.Blob)) throw new Error("Browser cannot create blobs");
        if (!(this.has_download_attribute || window.saveAs || navigator.msSaveOrOpenBlob)) throw new Error("Browser cannot save blobs");
        this.file_data = new t(12e6), this.central_directory = new t(5200), this.cdr_count = 0, this.filename = e, this.total_requests = 0, this.request_count = 0;
        for (var r = 0; r < n.length; r++) this.add_url(n[r][0], n[r][1]);
        this.done = function () {}, this.progress = function (e, t) {}, this.send_progress()
    }, e.zipfile.prototype.CENTRAL_DIRECTORY_HEADER = "PK", e.zipfile.prototype.LOCAL_FILE_HEADER = "PK", e.zipfile.prototype.CENTRAL_DIRECTORY_HEADER_END = "PK", e.zipfile.prototype.shared_header = function (n, r) {
        var i = new t(26);
        return i.write_short(20), i.write_short(0), i.write_short(0), i.write_short(0), i.write_short(33), i.write_int(e.CRC.crc32(r)), i.write_int(r.length), i.write_int(r.length), i.write_short(n.length), i.write_short(0), i
    }, e.zipfile.prototype.add_file = function (e, t) {
        var n = this.shared_header(e, t),
            r = this.file_data.length;
        this.file_data.write_bytes(this.LOCAL_FILE_HEADER), this.file_data.extend(n), this.file_data.write_bytes(e + ""), this.file_data.write_array(t), this.cdr_count++, this.central_directory.write_bytes(this.CENTRAL_DIRECTORY_HEADER), this.central_directory.write_short(20), this.central_directory.extend(n), this.central_directory.write_short(0), this.central_directory.write_short(0), this.central_directory.write_short(0), this.central_directory.write_int(0), this.central_directory.write_int(r), this.central_directory.write_bytes(e), n = null
    }, e.zipfile.prototype.cdr_end = function () {
        var e = new t(50);
        return e.write_bytes(this.CENTRAL_DIRECTORY_HEADER_END), e.write_short(0), e.write_short(0), e.write_short(this.cdr_count), e.write_short(this.cdr_count), e.write_int(this.central_directory.length), e.write_int(this.file_data.length), e.write_short(0), e
    }, e.zipfile.prototype.save_blob = function () {
        var e = this.cdr_end(),
            n = new t(this.file_data.length + this.central_directory.length + e.length);
        return n.extend(this.file_data), n.extend(this.central_directory), n.extend(e), n.to_blob("application/zip")
    }, e.zipfile.prototype.download = function () {
        var e = this.save_blob(),
            t = URL.createObjectURL(e);
        if (window.saveAs) saveAs(e, this.filename);
        else if (navigator.msSaveOrOpenBlob) navigator.msSaveOrOpenBlob(e, this.filename);
        else {
            if (!this.has_download_attribute) throw new Error("Your browser can't handle JS downloads");
            var n = document.createElement("a");
            n.style.display = "none", n.href = t, n.download = this.filename, document.body.appendChild(n), n.click(), setTimeout(function () {
                document.body.removeChild(n), window.URL.revokeObjectURL(t)
            }, 3e3)
        }
    }, e.zipfile.prototype.add_url = function (t, n) {
        var r = this;
        this.request_count++, this.total_requests++, e.http({
            method: "GET",
            url: n,
            xhr_properties: {
                responseType: "arraybuffer"
            },
            success: function () {
                r.add_file(t, new Uint8Array(this.response)), r.file_downloaded()
            },
            failure: function (t) {
                r.error(t)
            }
        })
    }, e.zipfile.prototype.file_downloaded = function () {
        this.request_count--, this.send_progress(), this.request_count === 0 && this.done()
    }, e.zipfile.prototype.send_progress = function () {
        this.progress(this.total_requests - this.request_count, this.total_requests)
    }, e.zipfile.prototype.error = function (e) {
        alert(e)
    }, "use strict", e.tag_autocomplete = function (t) {
        this.type = t, this.$wrapper = e.create_element('\n		<div class="tag-autocomplete">\n			<div class="autocomplete-wrapper">\n				<input type="text" class="tag tag-input" placeholder="enter a tag name" />\n				<ul class="dropdown"></ul>\n			</div>\n\n			<div class="tag-wrapper"></div>\n		</div>\n	'), this.$autocomplete_wrapper = this.$wrapper.querySelector(".autocomplete-wrapper"), this.$tag_wrapper = this.$wrapper.querySelector(".tag-wrapper"), this.$input = this.$autocomplete_wrapper.querySelector("input"), this.$dropdown = this.$autocomplete_wrapper.querySelector("ul"), this.install_handlers()
    }, e.tag_autocomplete.prototype.install_handlers = function () {
        var t = this;
        e.bind(this.$input, "keydown", function (n) {
            var r = t.$dropdown.querySelector("li.active"),
                i = t.$dropdown.querySelector("li"),
                s = !!r;
            r = r || i;
            if (this.value === "" && n.which === 8) n.preventDefault(), this.focus(), t.$wrapper.parentNode.removeChild(t.$wrapper);
            else if (n.which == e.key.UP) r.classList.remove("active"), (r.previousElementSibling || r).classList.add("active");
            else if (n.which == e.key.DOWN) s ? (r.classList.remove("active"), (r.nextElementSibling || r).classList.add("active")) : r.classList.add("active");
            else if (n.which == e.key.ENTER) {
                if (t.$dropdown.children.length == 1) r = t.$dropdown.children[0];
                else if (!s) return;
                var o = r.querySelector("a");
                t.select_tag(o)
            }
        }), e.bind(this.$dropdown, "li", "click", function (e) {
            t.select_tag(this), e.preventDefault()
        }), e.bind(this.$dropdown, "li", "hover", function (e) {
            var n = t.$dropdown.querySelector("li.active");
            if (n == this) return;
            n && n.classList.remove("active"), this.classList.add("active")
        }), e.bind(this.$tag_wrapper, ".tag", "click", function (n) {
            var r = t.$tag_wrapper.children[0],
                i = "";
            r && (i = e.strip(r.childNodes[0].nodeValue)), t.$autocomplete_wrapper.classList.remove("hidden"), t.$tag_wrapper.innerHTML = "", t.$input.value = i, t.$input.focus(), n.preventDefault()
        }, !0), e.bind(this.$input, "keyup", function (e) {
            t.show_autocomplete(this.value)
        })
    }, e.tag_autocomplete.prototype.select_tag = function (e) {
        e.classList.add("tag-added"), this.$tag_wrapper.appendChild(e), this.$autocomplete_wrapper
            .classList.add("hidden")
    }, e.tag_autocomplete.prototype.show_autocomplete = function (t) {
        var n = this;
        e.http({
            url: "/api/autocomplete",
            method: "POST",
            params: {
                name: t,
                type: this.type
            },
            success: function () {
                var i = JSON.parse(this.response),
                    s = [],
                    o = !1;
                for (var u = 0; u < i.result.length; u++) {
                    var a = i.result[u],
                        f = new e.tag(a.id, a.name, a.type, a.url, a.count);
                    a.name == t && (o = !0), s.push("<li>" + f.as_html() + "</li>")
                }!o && e.strip(t) && s.push("<li>" + (new e.tag(0, e.strip(t), this.type, "#", "create")).as_html() + "</li>"), n.$dropdown.innerHTML = s.join("")
            }
        })
    }, "use strict", e.tag = function (e, t, n, r, i) {
        this.id = e, this.name = t, this.type = n, this.url = r, this.count = i
    }, e.tag.prototype.as_html = function () {
        return e.render_template('\n		<a href="<%= tag.url %>" class="tag tag-<%= tag.id %>">\n			<%= tag.name %> <span class="count">(<%= N.pretty_integer(tag.count) %>)</span>\n		</a>\n	', {
            tag: this
        })
    }, e.gallery = function (t) {
        for (var n = 0; n < t.tags.length; n++) {
            var r = t.tags[n];
            t.tags[n] = new e.tag(r.id, r.name, r.type, r.url, r.count)
        }
        e.extend(this, t), this.editing = !1
    }, e.gallery.prototype.get_url = function (t) {
        return e.format("/g/{0}/{1}", this.id, t)
    }, e.gallery.prototype.toggle_favorite = function () {
        document.querySelector("#favorite").disabled = !0, e.http({
            method: "POST",
            url: this.get_url("favorite"),
            success: function () {
                var t = JSON.parse(this.response),
                    n = document.querySelector("#favorite i.fa");
                document.querySelector("#favorite .count").textContent = t.count, document.querySelector("#favorite .text").textContent = t.favorited ? "Unfavorite" : "Favorite", document.querySelector("#favorite").disabled = !1, n.classList.toggle("fa-heart"), n.classList.toggle("fa-heart-o")
            }
        })
    }, e.gallery.prototype.download_zipfile = function () {
        var t = [],
            n = this.images.pages.toString().length;
        for (var r = 0; r < this.images.pages.length; r++) {
            var i = e.pad((r + 1).toString(), n, "0"),
                s = {
                    j: "jpg",
                    p: "png",
                    g: "gif"
                }[this.images.pages[r].t],
                o = e.format("//i.nhentai.net/galleries/{0}/{1}.{2}", this.media_id, r + 1, s);
            t.push([e.format("{0}.{1}", i, s), o])
        }
        var u = new e.zipfile(this.title.pretty + ".zip", t);
        u.done = function () {
            u.download()
        };
        var a = document.querySelector("#download");
        u.progress = function (t, n) {
            a.textContent = e.format("Downloaded {0} of {1}: {2}%", t, n, (100 * t / n).toFixed(2))
        }
    }, e.gallery_editor = function (e) {
        this.gallery = e, this.editing = !1
    }, e.gallery_editor.prototype.toggle_edit = function () {
        this.editing ? (document.querySelector("#edit .text").textContent = "Edit", this.end_edit()) : (document.querySelector("#edit .text").textContent = "Save", this.begin_edit())
    }, e.gallery_editor.prototype.begin_edit = function () {
        if (this.editing) throw new Error("Gallery is already being edited");
        this.editing = !0, this.$containers = document.querySelectorAll(".tag-container"), this.tag_click_callback = e.bind("#info", ".tags .tag", "click", function (e) {
            if (this.classList.contains("tag-new") || this.classList.contains("tag-added")) return;
            this.classList.toggle("tag-deleted"), e.preventDefault()
        });
        for (var t = 0; t < this.$containers.length; t++) {
            var n = this.$containers[t];
            n.classList.remove("hidden");
            var r = document.createElement("a");
            r.classList.add("tag"), r.classList.add("tag-new"), r.textContent = "+",
                function (t) {
                    e.bind(r, "click", function () {
                        var n = new e.tag_autocomplete(t);
                        this.parentNode.insertBefore(n.$wrapper, this), n.$input.focus(), n.show_autocomplete("")
                    })
                }(n.dataset.type), n.appendChild(r)
        }
    }, e.gallery_editor.prototype.end_edit = function () {
        if (!this.editing) throw new Error("Gallery is not being edited");
        this.editing = !1, e.unbind("#info", "click", this.tag_click_callback, !0);
        var t = [].slice.call(document.querySelectorAll(".tag-new"));
        while (t.length) {
            var n = t.shift();
            n.parentNode.removeChild(n)
        }
        for (var r = 0; r < this.$containers.length; r++) {
            var i = this.$containers[r];
            i.querySelector(".tags > a") || i.classList.add("hidden")
        }
        var s = {
                english_title: null,
                japanese_title: null,
                added_tags: [],
                removed_tags: []
            },
            o = document.querySelectorAll(".tag-added, .tag-deleted");
        for (var r = 0; r < o.length; r++) {
            var u = o.length[r],
                a = e.strip(u.childNodes[0].nodeValue),
                f = e.find_parent(u, ".tag-container").dataset.type;
            u.classList.contains("tag-added") ? s.added_tags.push([a, f]) : s.removed_tags.push([a, f])
        }
        e.http({
            url: this.gallery.get_url("edit"),
            method: "POST",
            json: s,
            success: function (t) {
                window.location.reload()
            },
            failure: function (t) {
                window.location.reload()
            }
        })
    }
}).call(this);
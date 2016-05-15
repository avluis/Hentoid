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
    var e = window.nhentai = {};
    e.init = function () {
            for (var e in this) this.hasOwnProperty(e) && /^(install|patch)_/.test(e) && this[e]()
        }, e.install_analytics = function () {
            window.GoogleAnalyticsObject = "ga", e.analytics = window.ga = window.ga || function () {
                (window.ga.q = window.ga.q || []).push(arguments)
            }, window.ga.l = +(new Date), e.inject_script("https://www.google-analytics.com/analytics.js"), e.analytics("create", "UA-52345677-1", {
                sampleRate: 1
            }), e.analytics("send", "pageview")
        }, e.patch_safari_touch_hover = function () {
            e.bind(document, "touchstart", function () {}, !0)
        }, "use strict", e.bind = function () {
            return typeof arguments[2] == "string" ? e.bind_dynamic.apply(null, arguments) : e.bind_regular.apply(null, arguments)
        }, e.bind_dynamic = function (t, n, r, i) {
            return e.bind_regular(t, r, function (t) {
                var r = t.target;
                while (r.parentNode && !e.matches(r, n)) r = r.parentNode;
                if (e.matches(r, n)) return i.apply(r, arguments)
            }, !0)
        }, e.bind_regular = function (e, t, n, r) {
            typeof e == "string" ? e = document.querySelectorAll(e) : !(e instanceof NodeList) && e.constructor !== Array && (e = [e]);
            var t = t.split(/\s+/);
            for (var i = 0; i < e.length; i++) {
                var s = e[i];
                for (var i = 0; i < t.length; i++) s.addEventListener(t[i], n, !!r)
            }
        }, e.matches = function (e, t) {
            var n = e.matches || e.matchesSelector || e.webkitMatchesSelector || e.mozMatchesSelector || e.msMatchesSelector;
            if (n) return n.call(e, t);
            var r = document.querySelectorAll(t);
            for (var i = 0; i < r.length; i++)
                if (r[i] === e) return !0;
            return !1
        }, e.format = function (e) {
            var t = e.toString(),
                n = typeof arguments[1],
                r = n === "string" || n === "number" ? [].slice.call(arguments, 1) : arguments[1];
            for (var i in r) t = t.replace(RegExp("\\{" + i + "\\}", "gi"), r[i]);
            return t
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
        }, e.render_template = function (t, n) {
            var r = "\n		var parts = [];\n\n		with (context) {\n			parts.push('" + t.replace(/[\r\t\n]/g, " ").split("<%").join("	").replace(/((^|%>)[^\t]*)'/g, "$1\r").replace(/\t!=(.*?)%>/g, "',$1,'").replace(/\t=(.*?)%>/g, "',nhentai.html_escape($1),'").split("	").join("');").split("%>").join("parts.push('").split("\r").join("\\'") + "');\n		}\n\n		return parts.join('');";
            return n.nhentai = e, (new Function("context", r))(n)
        }, e.pretty_integer = function (e) {
            return e.toString().replace(/\B(?=(?:\d{3})+(?!\d))/g, ",")
        }, e.intervalometer = function (e, t, n, r) {
            var i = 0,
                s = setInterval(function () {
                    i++, (e() || n && i > n || i * t > r) && clearInterval(s)
                }, t);
            return s
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
                r = e.keys(t);
            for (var i = 0; i < r.length; i++) n.push(e.format("{0}={1}", encodeURIComponent(r[i]), encodeURIComponent(t[r[i]])));
            return "&".join(n)
        }, e.http = function (t) {
            e.extend(t, {
                headers: {},
                method: "GET",
                success: function () {},
                failure: function () {},
                xhr_properties: {},
                params: null,
                json: null
            }, !0);
            var n = new XMLHttpRequest;
            n.open(t.method, t.url, !0);
            var r = e.keys(t.headers);
            for (var i = 0; i < r.length; i++) n.setRequestHeader(r[i], t.headers[r[i]]);
            e.bind(n, "load", t.success), e.bind(n, "error", t.failure);
            var s = null;
            return t.params !== null ? s = e.encode_params(t.params) : t.json !== null && (s = JSON.stringify(t.json)), e.extend(n, t.xhr_properties), n.send(s), n
        }, "use strict", e.browser = {}, e.browser.supports_transform = function () {
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
        }(), e.browser.is_safari = !!navigator.userAgent.match(/Version\/[\d\.]+.*Safari/), "use strict", e.cookies = {}, e.cookies.get = function (e, t) {
            var n = document.cookie.split(/;\s*/);
            for (var r = 0; r < n.length; r++)
                if (n[r].indexOf(e + "=") === 0) return n[r].split("=", 1)[1];
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
        }, "use strict", e.session_storage = {}, e.session_storage.set = function (e, t) {
            sessionStorage.setItem(e, JSON.stringify(t))
        }, e.session_storage.has = function (e) {
            return sessionStorage.getItem(e) !== null
        }, e.session_storage.get = function (t, n) {
            return e.session_storage.has(t) ? JSON.parse(sessionStorage.getItem(t)) : n
        }, e.session_storage["delete"] = function (e) {
            sessionStorage.removeItem(e)
        }, e.cookie_storage = {}, e.cookie_storage.set =
        function (t, n) {
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
        }(), "use strict", e.reader = function (t) {
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
            return e.format("{0}galleries/{1}/{2}.{3}", this.media_url, this.gallery.media_id, t, this.get_extension(t))
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
            e.bind(document, "keydown", function (e) {
                if (e.target.tagName.toLowerCase() === "input" || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
                n && (clearInterval(n), n = null);
                var r = !0;
                e.which === 83 || e.which === 40 ? n = setInterval(function () {
                    window.scrollBy(0, 5)
                }, 5) : e.which === 87 || e.which === 38 ? n = setInterval(function () {
                    window.scrollBy(0, -5)
                }, 5) : e.which === 37 || e.which === 65 ? t.previous_page() : e.which === 39 || e.which === 68 ? t.next_page() : r = !1, r && e.preventDefault()
            }), e.bind(document, "keyup visibilitychange", function () {
                n && (clearInterval(n), n = null)
            })
        }, e.reader.prototype.install_link_catcher = function () {
            var t = this;
            e.bind("#page-container", ".pagination a", "click", function (e) {
                var n =
                    this.href.match(new RegExp("/g/\\d+/(\\d+)"));
                n && (e.preventDefault(), t.set_page(parseInt(n[1], 10), !0))
            })
        }, e.reader.prototype.install_image_navigation = function () {
            var t = this;
            e.bind("#image-container", "img", "click", function (e) {
                e.preventDefault();
                if (!t.click_debouncer.is_ready()) return;
                t.click_debouncer.hit();
                var n = e.clientX / this.width;
                n < .4 ? (console.log("You tapped left"), t.previous_page()) : (console.log("You tapped right"), t.current_page === t.num_pages ? window.location = t.gallery_url : t.next_page())
            })
        }, e.reader.prototype.install_touch = function () {
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
            e.bind(this.$image_container, "touchstart", function (e) {
                r = !1, i.x = e.targetTouches[0].pageX, i.y = e.targetTouches[0].pageY
            }), e.bind(this.$image_container, "touchmove", function (o) {
                s.x = o.targetTouches[0].pageX, s.y = o.targetTouches[0].pageY, Math.abs(i.x - s.x) > 10 && (r = !0);
                if (!r) return;
                o.stopPropagation();
                var u = s.x - i.x;
                t.current_page === 1 && u > 0 || t.current_page === t.num_pages && u < 0 ? u = e.sgn(u) * Math.pow(Math.abs(u), .65) : u = e.clamp(u, -n, n), e.browser.supports_transform ? (t.$image.style.transform = "translate3d(" + u + "px, 0px, 0px)", t.$image.style.transition = "0s") : t.$image.style.left = u + "px"
            }), e.bind(this.$image_container, "touchend", function (o) {
                s.x = o.changedTouches[0].pageX, s.y = o.changedTouches[0].pageY, r = !1, e.browser.supports_transform ? (t.$image.style.transform = "translate3d(0px, 0px, 0px)", t.$image.style.transition = "0.3s") : t.$image.style.left = "0px";
                var u = i.x - s.x;
                console.log("delta is", u), u > n ? (t.click_debouncer.hit(), t.next_page()) : u < -n && (t.click_debouncer.hit(), t.previous_page())
            }), e.bind(this.$image_container, "touchcancel", function (e) {
                r = !1, this.dispatchEvent(new CustomEvent("touchend"))
            })
        }, "use strict", e.install_theme_previewer = function () {
            var t = document.querySelector("html"),
                n = document.querySelector("#id_theme");
            if (!n) return;
            e.bind(n, "change", function () {
                var e = t.classList;
                for (var n = 0; n < e.length; n++) e[n].indexOf("theme-") === 0 && e.remove(e[n]);
                e.add("theme-" + this.value.toLowerCase())
            })
        }, "use strict", e.lazy_loader = function (e) {
            this.$elements = [].slice.call(e), this.$elements.reverse(), this.interval = setInterval(this.show_images.bind(this), 100);
            for (var t = 0; t < this.$elements.length; t++) {
                var n = e[t];
                n.src = this.placeholder(+n.getAttribute("width"), +n.getAttribute("height"))
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
                var i = e.relative_time(n[r].getAttribute("datetime"));
                n[r].innerHTML !== i && (n[r].innerHTML = i), i.indexOf("second") !== -1 && (t = 1e3)
            }
            return t
        }, e.install_relative_time = function () {
            var t = function n() {
                var t = e.update_times();
                setTimeout(n, t)
            };
            t()
        }, "use strict", e.install_frame_buster = function () {
            if (["localhost", "nhentai.net", "127.0.0.1"].indexOf(window.location.hostname) !== -1) return;
            document.body.style = "display: none", window.location = "http://nhentai.net" + window.location.pathname + window.location.search + window.location.hash
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
                var n = new(window.BlobBuilder || window.WebKitBlobBuilder ||
                        window.MozBlobBuilder || window.MSBlobBuilder),
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
        }, "use strict", e.tag = function (e, t, n, r, i) {
            this.id = e, this.name = t, this.type = n, this.url = r, this.count = i
        }, e.tag.prototype.as_html = function () {
            return e.render_template('\n		<a href="<%= tag.url %>" class="tag tag-<%= tag.id %>">\n			<%= tag.name %> <span class="count">(<%= nhentai.pretty_integer(tag.count) %>)</span>\n		</a>\n	', {
                tag: this
            })
        }, e.gallery = function (t) {
            for (var n = 0; n < t.tags.length; n++) {
                var r = t.tags[n];
                t.tags[n] = new e.tag(r.id, r.name, r.type, r.url, r.count)
            }
            e.extend(this, t), this.editing = !1
        }, e.gallery.prototype.get_favorite_url = function () {
            return e.format("/g/{0}/favorite", this.id)
        }, e.gallery.prototype.toggle_favorite = function (t) {
            document.querySelector("#favorite").disabled = !0, e.http({
                method: "POST",
                url: this.get_favorite_url(),
                headers: {
                    "X-CSRFToken": t,
                    "X-Requested-With": "XMLHttpRequest"
                },
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
            console.log(t);
            var u = new e.zipfile(this.title.pretty + ".zip", t);
            u.done = function () {
                u.download()
            };
            var a = document.querySelector("#download");
            u.progress = function (t, n) {
                a.textContent = e.format("Downloaded {0} of {1}: {2}%", t, n, (100 * t / n).toFixed(2))
            }
        }, e.gallery.prototype.begin_edit = function () {
            if (this.editing) throw new Error("Gallery is already being edited");
            var t = document.querySelectorAll(".tag-container");
            for (var n = 0; n < t.length; n++) {
                var r = t[n];
                r.classList.remove("hidden");
                var i = document.createElement("a");
                i.classList.add("tag"), i.classList.add("tag-new"), i.textContent = "+", e.bind(i, "click", function () {
                    var t = document.createElement("input");
                    t.classList.add("tag-input"), t.type = "text", e.bind(t, "change", function () {}), this.parentNode.insertBefore(t, this)
                }), r.appendChild(i)
            }
        }, e.gallery.prototype.end_edit = function () {
            if (!this.editing) throw new Error("Gallery is not being edited")
        }
}).call(this);
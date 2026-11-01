(function () {
  "use strict";

  var THEME_KEY = "codexonia-theme";

  var NAV_ITEMS = [
    { href: "/", label: "Home", key: "home" },
    { href: "/submit", label: "Submit Code", key: "submit" },
    { href: "/docs", label: "Field Guide", key: "docs" },
    { href: "/about", label: "About", key: "about" }
  ];

  function currentPageKey() {
    var page = document.body ? document.body.dataset.page : "";
    if (page) return page;
    var path = window.location.pathname.replace(/\/+$/, "");
    if (path === "" || path === "/" || path === "/index.html") return "home";
    if (path.indexOf("/submit") === 0) return "submit";
    if (path.indexOf("/docs") === 0) return "docs";
    if (path.indexOf("/about") === 0) return "about";
    return "";
  }

  function isDark() {
    return document.documentElement.classList.contains("dark");
  }

  function applyTheme(dark, persist) {
    document.documentElement.classList.toggle("dark", dark);
    if (document.body) document.body.classList.toggle("dark", dark);

    if (persist) {
      try {
        localStorage.setItem(THEME_KEY, dark ? "dark" : "light");
      } catch (error) {
        /* ignore storage failures */
      }
    }

    document.querySelectorAll("[data-theme-icon]").forEach(function (icon) {
      icon.textContent = dark ? "light_mode" : "dark_mode";
    });

    document.querySelectorAll("[data-theme-toggle]").forEach(function (button) {
      button.setAttribute("aria-label", dark ? "Switch to light mode" : "Switch to dark mode");
      button.setAttribute("title", dark ? "Switch to light mode" : "Switch to dark mode");
    });

    window.dispatchEvent(new CustomEvent("codexonia:themechange", { detail: { dark: dark } }));
  }

  function navLinks(className) {
    var active = currentPageKey();
    return NAV_ITEMS.map(function (item) {
      var isActive = item.key === active;
      return (
        '<a class="' + className + '" href="' + item.href + '"' +
        (isActive ? ' aria-current="page"' : "") + ">" +
        item.label +
        "</a>"
      );
    }).join("");
  }

  function renderHeader() {
    var mount = document.getElementById("site-header");
    if (!mount) return;

    var onSubmitPage = currentPageKey() === "submit";

    mount.innerHTML =
      '<header class="site-header">' +
        '<div class="shell header-inner">' +
          '<a class="brand" href="/" aria-label="Codexonia home">' +
            '<img class="brand-mark" src="/assets/codexonia-mark.svg" alt="" width="36" height="36">' +
            '<span class="brand-copy">' +
              '<span class="brand-name">Codexonia</span>' +
              '<span class="brand-tag">execution, made visible</span>' +
            "</span>" +
          "</a>" +
          '<nav class="nav-pill" aria-label="Primary">' + navLinks("nav-link") + "</nav>" +
          '<div class="header-actions">' +
            '<button class="icon-btn" type="button" data-theme-toggle aria-label="Switch to dark mode" title="Switch to dark mode">' +
              '<span class="material-symbols-outlined" data-theme-icon>dark_mode</span>' +
            "</button>" +
            (onSubmitPage ? "" :
              '<a class="btn btn-primary header-cta" href="/submit">' +
                'Open editor <span class="material-symbols-outlined">arrow_forward</span>' +
              "</a>") +
            '<button class="icon-btn nav-toggle" type="button" data-nav-toggle aria-label="Open menu" aria-expanded="false">' +
              '<span class="material-symbols-outlined" data-nav-icon>menu</span>' +
            "</button>" +
          "</div>" +
        "</div>" +
        '<div class="mobile-panel" data-nav-panel hidden>' +
          '<div class="shell mobile-panel-inner">' +
            navLinks("mobile-link").replace(/<\/a>/g, '<span class="material-symbols-outlined">arrow_outward</span></a>') +
            (onSubmitPage ? "" : '<a class="btn btn-primary mobile-cta" href="/submit">Open the editor</a>') +
          "</div>" +
        "</div>" +
      "</header>";

    var toggle = mount.querySelector("[data-nav-toggle]");
    var panel = mount.querySelector("[data-nav-panel]");
    var icon = mount.querySelector("[data-nav-icon]");

    if (toggle && panel) {
      toggle.addEventListener("click", function () {
        var open = panel.hasAttribute("hidden");
        if (open) {
          panel.removeAttribute("hidden");
        } else {
          panel.setAttribute("hidden", "");
        }
        toggle.setAttribute("aria-expanded", String(open));
        toggle.setAttribute("aria-label", open ? "Close menu" : "Open menu");
        if (icon) icon.textContent = open ? "close" : "menu";
      });

      panel.querySelectorAll("a").forEach(function (link) {
        link.addEventListener("click", function () {
          panel.setAttribute("hidden", "");
          toggle.setAttribute("aria-expanded", "false");
          toggle.setAttribute("aria-label", "Open menu");
          if (icon) icon.textContent = "menu";
        });
      });
    }

    mount.querySelectorAll("[data-theme-toggle]").forEach(function (button) {
      button.addEventListener("click", function () {
        applyTheme(!isDark(), true);
      });
    });

    applyTheme(isDark(), false);

    window.addEventListener("resize", function () {
      if (window.innerWidth >= 1024) {
        panel.setAttribute("hidden", "");
        toggle.setAttribute("aria-expanded", "false");
        if (icon) icon.textContent = "menu";
      }
    });
  }

  function renderFooter() {
    var mount = document.getElementById("site-footer");
    if (!mount) return;

    mount.innerHTML =
      '<footer class="site-footer">' +
        '<div class="shell footer-grid">' +
          '<div class="footer-brand">' +
            '<a class="brand" href="/" aria-label="Codexonia home">' +
              '<img class="brand-mark" src="/assets/codexonia-mark.svg" alt="" width="36" height="36">' +
              '<span class="brand-copy">' +
                '<span class="brand-name">Codexonia</span>' +
                '<span class="brand-tag">execution, made visible</span>' +
              "</span>" +
            "</a>" +
            "<p>A flight recorder for Java. Run a program, keep the evidence, and see the exact moment behavior happens.</p>" +
          "</div>" +
          '<div class="footer-col">' +
            "<h3>Product</h3>" +
            '<a href="/submit">Submit code</a>' +
            '<a href="/docs">Field guide</a>' +
            '<a href="/docs#api">HTTP API</a>' +
          "</div>" +
          '<div class="footer-col">' +
            "<h3>Project</h3>" +
            '<a href="/about">About Codexonia</a>' +
            '<a href="/privacy">Privacy</a>' +
            '<a href="/terms">Terms</a>' +
          "</div>" +
          '<div class="footer-col">' +
            "<h3>Under the hood</h3>" +
            "<span>Java 26 + Spring Boot</span>" +
            "<span>JDI tracing in Docker</span>" +
            "<span>FNV-1a deterministic audio</span>" +
          "</div>" +
        "</div>" +
        '<div class="shell footer-bottom">' +
          '<span>© <span data-year>2026</span> Codexonia. Built for CS students.</span>' +
        "</div>" +
      "</footer>";

    document.querySelectorAll("[data-year]").forEach(function (node) {
      node.textContent = String(new Date().getFullYear());
    });
  }

  function initReveal() {
    var items = document.querySelectorAll("[data-reveal]");
    if (!items.length) return;

    var reduced = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduced || !("IntersectionObserver" in window)) {
      items.forEach(function (item) { item.classList.add("is-visible"); });
      return;
    }

    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add("is-visible");
        observer.unobserve(entry.target);
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.08 });

    items.forEach(function (item) { observer.observe(item); });
  }

  function initCopyButtons() {
    document.querySelectorAll("[data-copy]").forEach(function (button) {
      button.addEventListener("click", function () {
        var target = document.getElementById(button.dataset.copy);
        if (!target) return;
        var text = target.innerText;
        var original = button.innerHTML;
        var done = function () {
          button.innerHTML = '<span class="material-symbols-outlined">check</span> Copied';
          window.setTimeout(function () { button.innerHTML = original; }, 1600);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(done).catch(function () {});
        } else {
          var area = document.createElement("textarea");
          area.value = text;
          document.body.appendChild(area);
          area.select();
          try { document.execCommand("copy"); } catch (error) {}
          area.remove();
          done();
        }
      });
    });
  }

  function init() {
    renderHeader();
    renderFooter();
    initReveal();
    initCopyButtons();
    applyTheme(isDark(), false);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();

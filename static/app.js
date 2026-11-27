(function () {
  "use strict";

  const hasNative = typeof window.AndroidBridge !== "undefined";

  // Synchronized Flat Monoline SVG Icons (Lucide 1.85px stroke, zero colorful emojis!)
  const ICONS = {
    zapSm: '<svg class="ic-sm" viewBox="0 0 24 24"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>',
    dots: '<svg class="ic" viewBox="0 0 24 24"><circle cx="5" cy="12" r="1.5" fill="currentColor"/><circle cx="12" cy="12" r="1.5" fill="currentColor"/><circle cx="19" cy="12" r="1.5" fill="currentColor"/></svg>',
    target: '<svg class="ic-sm" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="4"/></svg>',
    inbox: '<svg class="ic-sm" viewBox="0 0 24 24"><polyline points="22 12 16 12 14 15 10 15 8 12 2 12"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/></svg>',
    task: '<svg class="ic-sm" viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="3"/><path d="m9 12 2 2 4-4"/></svg>',
    arrowRight: '<svg class="ic-sm" viewBox="0 0 24 24"><path d="M5 12h14"/><path d="m13 6 6 6-6 6"/></svg>',
    arrowUpRight: '<svg class="ic-sm" viewBox="0 0 24 24"><path d="M7 17 17 7"/><path d="M7 7h10v10"/></svg>',
    plus: '<svg class="ic-xs" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
    plusSm: '<svg class="ic-sm" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
    check: '<svg class="ic-xs" viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"/></svg>',
    chevUp: '<svg class="ic-sm" viewBox="0 0 24 24"><polyline points="18 15 12 9 6 15"/></svg>',
    chevDown: '<svg class="ic-sm" viewBox="0 0 24 24"><polyline points="6 9 12 15 18 9"/></svg>',
    edit: '<svg class="ic-sm" viewBox="0 0 24 24"><path d="M17 3a2.85 2.85 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg>',
    copy: '<svg class="ic-sm" viewBox="0 0 24 24"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>',
    trash: '<svg class="ic-sm" viewBox="0 0 24 24"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>',
    folder: '<svg class="ic-sm" viewBox="0 0 24 24"><path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/></svg>',
    refresh: '<svg class="ic-sm" viewBox="0 0 24 24"><path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 16h5v5"/></svg>',
    chat: '<svg class="ic-sm" viewBox="0 0 24 24"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>',
    block: '<svg class="ic-sm" viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="6" rx="1.5"/><rect x="3" y="14" width="18" height="6" rx="1.5"/></svg>',
    sun: '<svg class="ic-sm" viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/></svg>',
    moon: '<svg class="ic-sm" viewBox="0 0 24 24"><path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/></svg>',
    star: '<svg class="ic-sm" viewBox="0 0 24 24"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>',
    starFill: '<svg class="ic-sm star-svg-on" viewBox="0 0 24 24"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" fill="#FBBF24" stroke="#FBBF24"/></svg>'
  };

  const state = {
    theme: localStorage.getItem("oq_theme") || "slate", // "slate" | "light"
    activeTab: "quick",
    notesDisplayMode: localStorage.getItem("oq_notes_display") || "chat",
    treeCollapsed: false,
    searchOpen: false,
    searchQuery: "",
    taskTarget: "focus",
    warehouseOpen: false,
    collapsedTaskIds: {},

    vaultConnected: false,
    vaultLabel: "",
    hasAllFilesAccess: false,
    lastKnownModified: 0,
    appVersion: "1.6.4",

    quickItems: [],
    noteStreams: [],
    activeStreamId: localStorage.getItem("oq_active_stream") || "",

    focusAnchorRaw: {},
    focusTasks: [],
    warehouseRawLines: [],
    warehouseTasks: [],
    vaultTags: ["#ui-ux", "#macbook", "#học-tập", "#mua-sắm", "#ý-tưởng"],
    linkPreviews: {}
  };

  const els = {
    topHeader: document.getElementById("topHeader"),
    hdrTitle: document.getElementById("hdrTitle"),
    hdrBadge: document.getElementById("hdrBadge"),
    btnSearchToggle: document.getElementById("btnSearchToggle"),
    btnSettings: document.getElementById("btnSettings"),
    searchBarWrap: document.getElementById("searchBarWrap"),
    searchInput: document.getElementById("searchInput"),
    btnSearchClear: document.getElementById("btnSearchClear"),
    vaultBanner: document.getElementById("vaultBanner"),
    vaultBannerText: document.getElementById("vaultBannerText"),
    btnConnectVault: document.getElementById("btnConnectVault"),

    viewQuick: document.getElementById("viewQuick"),
    viewNotes: document.getElementById("viewNotes"),
    viewTask: document.getElementById("viewTask"),

    quickList: document.getElementById("quickList"),
    pinnedTreeBox: document.getElementById("pinnedTreeBox"),
    twoColGrid: document.getElementById("twoColGrid"),
    leftColStreams: document.getElementById("leftColStreams"),
    rightColStreams: document.getElementById("rightColStreams"),
    drawerHandleZone: document.getElementById("drawerHandleZone"),
    notesStreamHeader: document.getElementById("notesStreamHeader"),
    activeStreamName: document.getElementById("activeStreamName"),
    notesStreamList: document.getElementById("notesStreamList"),

    focusTaskList: document.getElementById("focusTaskList"),
    btnToggleWarehouse: document.getElementById("btnToggleWarehouse"),
    warehouseHdrLabel: document.getElementById("warehouseHdrLabel"),
    warehouseChevron: document.getElementById("warehouseChevron"),
    warehouseTaskList: document.getElementById("warehouseTaskList"),

    tagSuggestBar: document.getElementById("tagSuggestBar"),
    btnTaskTargetToggle: document.getElementById("btnTaskTargetToggle"),
    composerInput: document.getElementById("composerInput"),
    btnVoice: document.getElementById("btnVoice"),
    btnSend: document.getElementById("btnSend"),
    bottomBar: document.getElementById("bottomBar"),
    badgeQuick: document.getElementById("badgeQuick"),
    badgeTask: document.getElementById("badgeTask"),

    sheetBackdrop: document.getElementById("sheetBackdrop"),
    bottomSheet: document.getElementById("bottomSheet"),
    sheetContent: document.getElementById("sheetContent"),
    focusEditOverlay: document.getElementById("focusEditOverlay"),
    focusEditCard: document.getElementById("focusEditCard"),
    focusEditInput: document.getElementById("focusEditInput"),
    focusEditChildren: document.getElementById("focusEditChildren"),
    btnFocusStar: document.getElementById("btnFocusStar"),
    btnFocusCancel: document.getElementById("btnFocusCancel"),
    btnFocusSave: document.getElementById("btnFocusSave"),
    toast: document.getElementById("toast")
  };

  let uidCounter = 1;
  function makeId(prefix) {
    return prefix + "_" + Date.now() + "_" + (uidCounter++);
  }

  function nowHHMM() {
    const d = new Date();
    return String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0");
  }

  function vibrate(ms) {
    if (hasNative && window.AndroidBridge.vibrate) {
      try { window.AndroidBridge.vibrate(ms || 12); } catch (_) {}
    }
  }

  let toastTimer = null;
  function showToast(msg) {
    els.toast.textContent = msg;
    els.toast.classList.remove("hidden");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => els.toast.classList.add("hidden"), 1900);
  }

  function escHtml(str) {
    return String(str || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function formatRichText(raw, hideStarPrefix) {
    let str = String(raw || "");
    let starHtml = "";
    if (/^★\s*/.test(str)) {
      str = str.replace(/^★\s*/, "");
      if (!hideStarPrefix) {
        starHtml = '<span class="star-inline">' + ICONS.starFill + "</span>";
      }
    }
    const safe = escHtml(str);
    return starHtml + safe.replace(/(#[\p{L}\p{N}_/-]+)/gu, '<span class="tag-pill">$1</span>');
  }

  // =========================================================================
  // NOTION WEB BOOKMARK CARDS & SHARED METADATA CACHE
  // =========================================================================
  const pendingPreviewUrls = new Set();
  const MD_LINK_GLOBAL_RE = /\[([^\]]+)\]\((?:<([^>\s]+)>|(https?:\/\/[^\s)]+))(?:\s+"[^"]*")?\)/g;
  const RAW_URL_GLOBAL_RE = /https?:\/\/[^\s<>()]+/g;

  try {
    const savedPreviews = JSON.parse(localStorage.getItem("oq_link_previews") || "{}");
    if (savedPreviews && typeof savedPreviews === "object") {
      state.linkPreviews = savedPreviews;
    }
  } catch (_) {}

  function persistLocalLinkPreviews() {
    try {
      const keys = Object.keys(state.linkPreviews);
      const compact = {};
      const start = Math.max(0, keys.length - 180);
      for (let i = start; i < keys.length; i++) compact[keys[i]] = state.linkPreviews[keys[i]];
      localStorage.setItem("oq_link_previews", JSON.stringify(compact));
    } catch (_) {}
  }

  function getLinkHost(url) {
    try {
      return new URL(url).hostname.replace(/^(www\.|m\.)/i, "") || url;
    } catch (_) {
      return String(url || "");
    }
  }

  function getGoogleFavicon(url) {
    const host = getLinkHost(url);
    if (!host || host.includes("/")) return "";
    return "https://www.google.com/s2/favicons?domain=" + encodeURIComponent(host) + "&sz=64";
  }

  function cleanTrailingUrlPunct(u) {
    return String(u || "").replace(/[.,;:!?]+$/, "");
  }

  function parseNoteContentParts(raw) {
    let str = String(raw || "");
    const starred = /^★\s*/.test(str);
    if (starred) str = str.replace(/^★\s*/, "");

    const links = [];
    const seen = new Set();
    let stripped = str.replace(MD_LINK_GLOBAL_RE, (full, label, angleUrl, plainUrl) => {
      const url = cleanTrailingUrlPunct(angleUrl || plainUrl || "");
      if (!/^https?:\/\//i.test(url)) return full;
      if (!seen.has(url)) {
        seen.add(url);
        links.push({ url: url, label: String(label || "").trim(), isRaw: false });
      }
      return " ";
    });

    stripped = stripped.replace(RAW_URL_GLOBAL_RE, (rawMatch) => {
      const url = cleanTrailingUrlPunct(rawMatch);
      const trail = rawMatch.slice(url.length);
      if (!/^https?:\/\//i.test(url)) return rawMatch;
      if (!seen.has(url)) {
        seen.add(url);
        links.push({ url: url, label: "", isRaw: true });
      }
      return trail ? (" " + trail) : " ";
    });

    const caption = stripped.replace(/\s+/g, " ").trim();
    return { starred: starred, caption: caption, links: links };
  }

  function requestLinkPreview(url) {
    if (!url || pendingPreviewUrls.has(url)) return;
    const cached = state.linkPreviews[url];
    const host = getLinkHost(url);
    if (cached && (cached.image || cached.description || (cached.title && cached.title !== host))) {
      return;
    }
    if (hasNative && window.AndroidBridge.fetchLinkPreview) {
      pendingPreviewUrls.add(url);
      try {
        window.AndroidBridge.fetchLinkPreview(url);
      } catch (_) {
        pendingPreviewUrls.delete(url);
      }
    }
  }

  function buildNotionBookmarkHtml(link) {
    const url = link.url;
    const host = getLinkHost(url);
    const meta = state.linkPreviews[url] || null;
    if (!meta) requestLinkPreview(url);

    const hasRealLabel = link.label && link.label.toLowerCase() !== "embed";
    const title = (meta && meta.title) ? meta.title : (hasRealLabel ? link.label : host);
    const desc = (meta && meta.description) ? meta.description : "";
    const site = (meta && meta.site) ? meta.site : host;
    const image = (meta && meta.image) ? meta.image : "";
    const favicon = (meta && meta.favicon) ? meta.favicon : getGoogleFavicon(url);
    const initial = (site || host || "L").trim().slice(0, 1).toUpperCase();

    const descHtml = desc
      ? '<div class="nb-desc">' + escHtml(desc) + "</div>"
      : (!meta ? '<div class="nb-desc nb-loading">' + escHtml(url.replace(/^https?:\/\//i, "")) + "</div>" : "");

    const thumbHtml = image
      ? '<div class="nb-thumb" data-initial="' + escHtml(initial) + '"><img src="' + escHtml(image) + '" loading="lazy" referrerpolicy="no-referrer" onerror="var p=this.parentElement;this.remove();if(p){p.classList.add(\'is-empty\');p.textContent=p.dataset.initial||\'L\';}" /></div>'
      : '<div class="nb-thumb is-empty">' + escHtml(initial) + "</div>";

    const favHtml = favicon
      ? '<img class="nb-favicon" src="' + escHtml(favicon) + '" loading="lazy" referrerpolicy="no-referrer" onerror="this.style.display=\'none\'" />'
      : "";

    return (
      '<div class="notion-bookmark-card" data-open-url="' + escHtml(url) + '">' +
        '<div class="nb-body">' +
          '<div class="nb-title">' + escHtml(title) + "</div>" +
          descHtml +
          '<div class="nb-meta">' + favHtml + '<span class="nb-site">' + escHtml(site) + "</span></div>" +
        "</div>" +
        thumbHtml +
      "</div>"
    );
  }

  function renderRichContentBlock(rawText, hideStarPrefix, textClass) {
    const parts = parseNoteContentParts(rawText);
    if (parts.links.length === 0) {
      return {
        hasBookmark: false,
        bookmarkOnly: false,
        html: '<div class="' + textClass + '">' + formatRichText(rawText, hideStarPrefix) + "</div>"
      };
    }
    const captionWithStar = (parts.starred && !hideStarPrefix) ? ("★ " + parts.caption) : parts.caption;
    const captionHtml = parts.caption
      ? '<div class="' + textClass + '">' + formatRichText(captionWithStar, hideStarPrefix) + "</div>"
      : "";
    const cardsHtml = '<div class="nb-stack">' + parts.links.map(buildNotionBookmarkHtml).join("") + "</div>";
    return {
      hasBookmark: true,
      bookmarkOnly: !parts.caption,
      html: captionHtml + cardsHtml
    };
  }

  function openBookmarkUrl(url) {
    if (!url) return;
    vibrate(8);
    if (hasNative && window.AndroidBridge.openExternalUrl) {
      try {
        window.AndroidBridge.openExternalUrl(url);
        return;
      } catch (_) {}
    }
    window.open(url, "_blank", "noopener,noreferrer");
  }

  function escapeMdLabel(title) {
    return String(title || "")
      .replace(/[\r\n]+/g, " ")
      .replace(/\[/g, "(")
      .replace(/\]/g, ")")
      .trim()
      .slice(0, 120);
  }

  function upgradeRawUrlInString(text, targetUrl, title) {
    const safeTitle = escapeMdLabel(title);
    if (!safeTitle) return { changed: false, next: text };
    const host = getLinkHost(targetUrl);
    if (safeTitle === targetUrl || safeTitle.toLowerCase() === host.toLowerCase()) {
      return { changed: false, next: text };
    }
    // Skip if already wrapped as Markdown link [..](targetUrl) or (<targetUrl>)
    if (text.includes("](" + targetUrl + ")") || text.includes("](<" + targetUrl + ">")) {
      return { changed: false, next: text };
    }
    let changed = false;
    const next = text.replace(RAW_URL_GLOBAL_RE, (m) => {
      const clean = cleanTrailingUrlPunct(m);
      if (clean === targetUrl) {
        changed = true;
        const trail = m.slice(clean.length);
        return "[" + safeTitle + "](" + clean + ")" + trail;
      }
      return m;
    });
    return { changed: changed, next: next };
  }

  function maybeUpgradeRawUrlsInVault(url, title) {
    if (!url || !title) return;
    let dirty = false;
    const editingItem = activeFocusEdit ? activeFocusEdit.item : null;

    const processList = (list) => {
      for (const it of list) {
        if (it === editingItem) continue;
        const r = upgradeRawUrlInString(it.text || "", url, title);
        if (r.changed) {
          it.text = r.next;
          dirty = true;
        }
        if (Array.isArray(it.children)) {
          for (let i = 0; i < it.children.length; i++) {
            const rc = upgradeRawUrlInString(it.children[i] || "", url, title);
            if (rc.changed) {
              it.children[i] = rc.next;
              dirty = true;
            }
          }
        }
      }
    };

    processList(state.quickItems);
    for (const st of state.noteStreams) processList(st.items);
    if (dirty) persistQuickNotes();
  }

  function applyTheme() {
    document.body.classList.remove("theme-slate", "theme-obsidian", "theme-light");
    if (state.theme === "light") {
      document.body.classList.add("theme-light");
      if (hasNative && window.AndroidBridge.syncSystemBars) {
        try { window.AndroidBridge.syncSystemBars("#F6F7FB", "#FFFFFF", true); } catch (_) {}
      }
    } else if (state.theme === "obsidian") {
      document.body.classList.add("theme-obsidian");
      if (hasNative && window.AndroidBridge.syncSystemBars) {
        try { window.AndroidBridge.syncSystemBars("#1A1826", "#221F32", false); } catch (_) {}
      }
    } else {
      document.body.classList.add("theme-slate");
      if (hasNative && window.AndroidBridge.syncSystemBars) {
        try { window.AndroidBridge.syncSystemBars("#17191F", "#1F222B", false); } catch (_) {}
      }
    }
  }

  function syncComposerButtons() {
    const hasText = els.composerInput.value.trim().length > 0;
    els.btnSend.classList.toggle("hidden", !hasText);
    els.btnVoice.classList.toggle("hidden", hasText);
  }

  // =========================================================================
  // PARSER & SERIALIZER: 0. Inbox/⚡ Quick Notes.md
  // =========================================================================
  function parseBulletLine(trimmedAfterDash) {
    const m = trimmedAfterDash.match(/^(\d{1,2}:\d{2})\s*[·•-]\s*(.+)$/);
    if (m) return { time: m[1], text: m[2].trim() };
    return { time: "", text: trimmedAfterDash.trim() };
  }

  function parseQuickNotesMd(md) {
    const quickItems = [];
    const streams = [];
    let section = "";
    let currentStream = null;
    let lastParentItem = null;

    const lines = String(md || "").replace(/\r\n/g, "\n").split("\n");
    for (const rawLine of lines) {
      const trimmed = rawLine.trim();
      if (!trimmed || trimmed === "---") continue;

      if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
        const h1 = trimmed.slice(2).trim();
        currentStream = null;
        lastParentItem = null;
        if (/ghi nhanh|nháp|quick/i.test(h1)) section = "quick";
        else if (/đang giải quyết|vấn đề/i.test(h1)) section = "active";
        else if (/kho|lưu trữ|cất tạm/i.test(h1)) section = "archive";
        else section = "active";
        continue;
      }

      if (trimmed.startsWith("## ")) {
        const title = trimmed.slice(3).trim();
        if (!title) continue;
        const grp = section === "archive" ? "archive" : "active";
        currentStream = { id: "st_" + grp + "_" + title, group: grp, title: title, items: [] };
        streams.push(currentStream);
        lastParentItem = null;
        continue;
      }

      if (/^(\t+| {2,})-\s+/.test(rawLine)) {
        const childText = rawLine.replace(/^(\t+| {2,})-\s+/, "").trim();
        if (childText && lastParentItem) lastParentItem.children.push(childText);
        continue;
      }

      if (trimmed.startsWith("- ")) {
        const parsed = parseBulletLine(trimmed.slice(2));
        if (!parsed.text) continue;
        const item = { id: makeId("ni"), time: parsed.time, text: parsed.text, children: [] };
        if (section === "quick" || (!currentStream && section !== "active" && section !== "archive")) {
          quickItems.push(item);
          lastParentItem = item;
        } else {
          if (!currentStream) {
            const defaultTitle = section === "archive" ? "Kho Notes chung" : "Bảng cảm giác phấn đấu";
            currentStream = { id: "st_" + section + "_" + defaultTitle, group: section === "archive" ? "archive" : "active", title: defaultTitle, items: [] };
            streams.push(currentStream);
          }
          currentStream.items.push(item);
          lastParentItem = item;
        }
      }
    }

    if (!streams.some((s) => s.group === "active")) {
      streams.unshift(
        { id: "st_active_Bảng cảm giác phấn đấu", group: "active", title: "Bảng cảm giác phấn đấu", items: [] },
        { id: "st_active_Những điều cốt lõi bản thân", group: "active", title: "Những điều cốt lõi bản thân", items: [] }
      );
    }
    if (!streams.some((s) => s.group === "archive")) {
      streams.push({ id: "st_archive_Kho Notes chung", group: "archive", title: "Kho Notes chung", items: [] });
    }

    state.quickItems = quickItems;
    state.noteStreams = streams;
    if (!state.activeStreamId || !streams.some((s) => s.id === state.activeStreamId)) {
      const firstActive = streams.find((s) => s.group === "active") || streams[0];
      state.activeStreamId = firstActive ? firstActive.id : "";
    }
  }

  function serializeQuickNotesMd() {
    const out = [];
    out.push("# ⚡ Ghi nhanh");
    for (const item of state.quickItems) {
      const prefix = item.time ? item.time + " · " : "";
      out.push("- " + prefix + item.text);
      for (const ch of item.children || []) out.push("\t- " + ch);
    }

    out.push("# 🎯 Đang giải quyết");
    for (const st of state.noteStreams.filter((s) => s.group === "active")) {
      out.push("## " + st.title);
      for (const item of st.items) {
        const prefix = item.time ? item.time + " · " : "";
        out.push("- " + prefix + item.text);
        for (const ch of item.children || []) out.push("\t- " + ch);
      }
    }

    out.push("# 📥 Kho lưu trữ chung");
    for (const st of state.noteStreams.filter((s) => s.group === "archive")) {
      out.push("## " + st.title);
      for (const item of st.items) {
        const prefix = item.time ? item.time + " · " : "";
        out.push("- " + prefix + item.text);
        for (const ch of item.children || []) out.push("\t- " + ch);
      }
    }
    return out.join("\n") + "\n";
  }

  function persistQuickNotes() {
    const md = serializeQuickNotesMd();
    if (hasNative) {
      window.AndroidBridge.saveQuickNotesMd(md);
      state.lastKnownModified = window.AndroidBridge.getVaultModifiedTimestamp();
    } else {
      localStorage.setItem("oq_web_fallback_md", md);
    }
  }

  // =========================================================================
  // PARSER & SERIALIZER: TASK & REMINDERS
  // =========================================================================
  const MD_REMIND_RE = /(?:📅\s*(\d{4}-\d{2}-\d{2})\s*)?⏰\s*(?:(\d{4}-\d{2}-\d{2})\s+)?(\d{1,2}:\d{2})/;
  let activeReminderPickerCb = null;

  function pad2(n) { return String(n).padStart(2, "0"); }

  function formatDateYMD(d) {
    return d.getFullYear() + "-" + pad2(d.getMonth() + 1) + "-" + pad2(d.getDate());
  }

  function formatRemindAtDate(d) {
    return formatDateYMD(d) + " " + pad2(d.getHours()) + ":" + pad2(d.getMinutes());
  }

  function normalizeRemindAt(raw) {
    const s = String(raw || "").trim().replace("T", " ").replace(/\s+/g, " ");
    const m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})/);
    if (!m) return "";
    return m[1] + "-" + pad2(parseInt(m[2], 10)) + "-" + pad2(parseInt(m[3], 10)) + " " +
      pad2(parseInt(m[4], 10)) + ":" + m[5];
  }

  function extractMarkdownReminder(rawText) {
    const str = String(rawText || "");
    const m = str.match(MD_REMIND_RE);
    if (!m) return { cleanText: str.trim(), remindAt: "" };
    const datePart = m[1] || m[2] || formatDateYMD(new Date());
    const [hh, mm] = m[3].split(":");
    const remindAt = normalizeRemindAt(datePart + " " + pad2(parseInt(hh, 10)) + ":" + mm);
    const cleanText = str.replace(MD_REMIND_RE, "").replace(/\s{2,}/g, " ").trim();
    return { cleanText: cleanText || str.trim(), remindAt };
  }

  function applyMarkdownReminder(rawText, remindAt) {
    const base = extractMarkdownReminder(rawText).cleanText;
    const norm = normalizeRemindAt(remindAt);
    if (!norm) return base;
    const [dPart, tPart] = norm.split(" ");
    const noDupDate = base.replace(/\s*📅\s*\d{4}-\d{2}-\d{2}/g, "").trim();
    return (noDupDate + " 📅 " + dPart + " ⏰ " + tPart).trim();
  }

  function buildReminderPreset(preset) {
    const d = new Date();
    d.setSeconds(0, 0);
    if (preset === "in_1h") {
      d.setHours(d.getHours() + 1);
    } else if (preset === "tonight_21") {
      const now = Date.now();
      d.setHours(21, 0, 0, 0);
      if (d.getTime() <= now + 60000) d.setDate(d.getDate() + 1);
    } else if (preset === "tomorrow_9") {
      d.setDate(d.getDate() + 1);
      d.setHours(9, 0, 0, 0);
    }
    return formatRemindAtDate(d);
  }

  function formatReminderBadge(remindAt) {
    const norm = normalizeRemindAt(remindAt);
    if (!norm) return null;
    const [dPart, tPart] = norm.split(" ");
    const [y, mo, da] = dPart.split("-").map(Number);
    const [hh, mm] = tPart.split(":").map(Number);
    const target = new Date(y, mo - 1, da, hh, mm, 0, 0);
    const now = new Date();
    const overdue = target.getTime() < now.getTime() - 30000;
    const todayStr = formatDateYMD(now);
    const tmr = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
    const tmrStr = formatDateYMD(tmr);
    let label = "⏰ " + da + "/" + mo + " " + tPart;
    if (dPart === todayStr) label = "⏰ " + tPart;
    else if (dPart === tmrStr) label = "⏰ Mai " + tPart;
    return { label, overdue };
  }

  function requestNativeReminderPicker(currentRemindAt, onPicked) {
    if (hasNative && typeof window.AndroidBridge.pickReminderDateTime === "function") {
      activeReminderPickerCb = (picked) => {
        activeReminderPickerCb = null;
        if (picked) onPicked(normalizeRemindAt(picked));
      };
      window.AndroidBridge.pickReminderDateTime(currentRemindAt || "");
    } else {
      const fallback = prompt("Nhập lịch nhắc (YYYY-MM-DD HH:mm):", currentRemindAt || buildReminderPreset("in_1h"));
      if (fallback !== null) {
        const norm = normalizeRemindAt(fallback);
        if (norm) onPicked(norm);
      }
    }
  }

  function ensureReminderPermissionSafe() {
    if (hasNative && typeof window.AndroidBridge.ensureReminderPermission === "function") {
      try { window.AndroidBridge.ensureReminderPermission(); } catch (_) {}
    }
  }

  function parseQuickTasksMd(md) {
    const lines = String(md || "").replace(/\r\n/g, "\n").split("\n");
    const focus = [];
    const wait = [];
    let inFocus = true;
    let curGroup = "Việc chờ";
    let lastParent = null;

    for (let i = 0; i < lines.length; i++) {
      const rawLine = lines[i];
      const tr = rawLine.trim();
      if (!tr || tr === "---") continue;

      if (tr.startsWith("# ") && !tr.startsWith("## ")) {
        const h1 = tr.slice(2).trim();
        lastParent = null;
        if (/Hôm nay|Trọng tâm/i.test(h1)) {
          inFocus = true;
        } else {
          inFocus = false;
          curGroup = "Việc chờ";
        }
        continue;
      }

      if (tr.startsWith("## ") || tr.startsWith("### ")) {
        const cleanGrp = tr.replace(/^#+\s*/, "").replace(/^[^\p{L}\p{N}]+/u, "").trim();
        lastParent = null;
        if (cleanGrp) {
          inFocus = false;
          curGroup = cleanGrp;
        }
        continue;
      }

      const subTaskM = rawLine.match(/^(\t+| {2,})-\s*\[([ xX])\]\s+(.+)$/);
      if (subTaskM && lastParent) {
        lastParent.subTasks = lastParent.subTasks || [];
        lastParent.subTasks.push({
          id: makeId("st"),
          done: subTaskM[2].toLowerCase() === "x",
          text: subTaskM[3].trim()
        });
        continue;
      }
      const subBulletM = rawLine.match(/^(\t+| {2,})-\s+(.+)$/);
      if (subBulletM && lastParent) {
        lastParent.subTasks = lastParent.subTasks || [];
        lastParent.subTasks.push({ id: makeId("st"), done: false, text: subBulletM[2].trim() });
        continue;
      }

      const m = tr.match(/^-\s*\[([ xX])\]\s+(.+)$/);
      if (m) {
        const done = m[1].toLowerCase() === "x";
        const rawBody = m[2].trim();
        const isStar = /^★\s*/.test(rawBody);
        const noStar = rawBody.replace(/^★\s*/, "").trim();
        const ext = extractMarkdownReminder(noStar);
        if (inFocus) {
          const item = {
            id: makeId("cf"),
            text: ext.cleanText || noStar,
            why: "",
            status: done ? "done" : "doing",
            isCore: isStar,
            notePath: null,
            subLabel: "",
            remindAt: ext.remindAt || "",
            subTasks: []
          };
          focus.push(item);
          lastParent = item;
        } else {
          const item = {
            id: makeId("wh"),
            group: curGroup || "Việc chờ",
            done: done,
            isCore: isStar,
            text: rawBody,
            cleanText: ext.cleanText || noStar,
            remindAt: ext.remindAt || "",
            subTasks: []
          };
          wait.push(item);
          lastParent = item;
        }
      }
    }

    state.focusTasks = focus;
    state.warehouseTasks = wait;
  }

  function serializeQuickTasksMd() {
    const out = ["# 🎯 Hôm nay"];
    for (const t of state.focusTasks) {
      const box = t.status === "done" ? "x" : " ";
      const starPfx = t.isCore ? "★ " : "";
      const body = applyMarkdownReminder(String(t.text || "").replace(/^★\s*/, "").trim(), t.remindAt || "");
      if (!body) continue;
      out.push("- [" + box + "] " + starPfx + body);
      for (const st of t.subTasks || []) {
        if (!st || !String(st.text || "").trim()) continue;
        out.push("\t- [" + (st.done ? "x" : " ") + "] " + String(st.text).trim());
      }
    }

    out.push("# 📥 Việc chờ");
    const defaultWait = state.warehouseTasks.filter((w) => !w.group || w.group === "Việc chờ");
    const groupOrder = [];
    const byGroup = {};
    for (const w of state.warehouseTasks) {
      if (!w.group || w.group === "Việc chờ") continue;
      if (!byGroup[w.group]) {
        byGroup[w.group] = [];
        groupOrder.push(w.group);
      }
      byGroup[w.group].push(w);
    }
    const emitWait = (arr) => {
      for (const w of arr) {
        const box = w.done ? "x" : " ";
        const starPfx = w.isCore ? "★ " : "";
        const body = applyMarkdownReminder(String(w.cleanText || w.text || "").replace(/^★\s*/, "").trim(), w.remindAt || "");
        if (!body) continue;
        out.push("- [" + box + "] " + starPfx + body);
        for (const st of w.subTasks || []) {
          if (!st || !String(st.text || "").trim()) continue;
          out.push("\t- [" + (st.done ? "x" : " ") + "] " + String(st.text).trim());
        }
      }
    };
    emitWait(defaultWait);
    for (const g of groupOrder) {
      out.push("## " + g);
      emitWait(byGroup[g]);
    }
    return out.join("\n") + "\n";
  }

  function persistQuickTasksMd() {
    const md = serializeQuickTasksMd();
    if (hasNative && typeof window.AndroidBridge.saveQuickTasksMd === "function") {
      window.AndroidBridge.saveQuickTasksMd(md);
      state.lastKnownModified = window.AndroidBridge.getVaultModifiedTimestamp();
    } else {
      localStorage.setItem("oq_web_fallback_tasks_md", md);
    }
  }

  function persistDailyAnchor() {
    persistQuickTasksMd();
  }

  function persistWarehouseMd() {
    persistQuickTasksMd();
  }

  let didInitialBottomScroll = false;

  function scrollActiveListToBottom() {
    setTimeout(() => {
      if (state.activeTab === "quick" && els.quickList) {
        els.quickList.scrollTop = els.quickList.scrollHeight;
      } else if (state.activeTab === "notes" && els.notesStreamList) {
        els.notesStreamList.scrollTop = els.notesStreamList.scrollHeight;
      }
    }, 25);
  }

  function loadVaultState() {
    if (hasNative) {
      try {
        const raw = JSON.parse(window.AndroidBridge.getFullVaultState());
        state.vaultConnected = !!raw.connected;
        state.vaultLabel = raw.vaultLabel || "";
        state.hasAllFilesAccess = !!raw.hasAllFilesAccess;
        state.lastKnownModified = raw.lastModified || 0;
        state.appVersion = raw.appVersion || "1.0.2";
        parseQuickNotesMd(raw.quickNotesMd);
        parseQuickTasksMd(raw.quickTasksMd || "# 🎯 Hôm nay\n# 📥 Việc chờ\n");
        try {
          const parsedTags = JSON.parse(raw.tagsJson || "[]");
          if (Array.isArray(parsedTags) && parsedTags.length > 0) {
            state.vaultTags = parsedTags.slice(0, 30).map((t) => {
              const s = typeof t === "string" ? t : t.tag || "";
              return s.startsWith("#") ? s : "#" + s;
            }).filter((t) => t.length > 1);
          }
        } catch (_) {}
        try {
          const parsedPreviews = JSON.parse(raw.linkPreviewsJson || "{}");
          if (parsedPreviews && typeof parsedPreviews === "object") {
            state.linkPreviews = Object.assign({}, state.linkPreviews, parsedPreviews);
            persistLocalLinkPreviews();
          }
        } catch (_) {}
      } catch (_) {}
    } else {
      state.vaultConnected = true;
      state.vaultLabel = "Obsidian (Bản chính)";
      parseQuickNotesMd(localStorage.getItem("oq_web_fallback_md") || "");
      parseQuickTasksMd(localStorage.getItem("oq_web_fallback_tasks_md") || "# 🎯 Hôm nay\n# 📥 Việc chờ\n");
    }
    applyTheme();
    renderAll();
    if (!didInitialBottomScroll) {
      didInitialBottomScroll = true;
      scrollActiveListToBottom();
    }
  }

  // =========================================================================
  // RENDERING
  // =========================================================================
  function getActiveStream() {
    return state.noteStreams.find((s) => s.id === state.activeStreamId) || state.noteStreams[0] || null;
  }

  function matchesQuery(text, children) {
    if (!state.searchQuery) return true;
    const q = state.searchQuery.toLowerCase();
    if (String(text || "").toLowerCase().includes(q)) return true;
    if (Array.isArray(children)) return children.some((c) => String(c || "").toLowerCase().includes(q));
    return false;
  }

  function renderHeaderAndNav() {
    if (!state.vaultConnected) {
      els.vaultBanner.classList.remove("hidden");
      els.vaultBannerText.textContent = "Chọn thư mục Vault để đồng bộ 2 chiều qua Syncthing";
      els.btnConnectVault.textContent = "Chọn thư mục";
    } else {
      els.vaultBanner.classList.add("hidden");
    }

    document.querySelectorAll(".nav-tab").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.tab === state.activeTab);
    });
    els.viewQuick.classList.toggle("active", state.activeTab === "quick");
    els.viewNotes.classList.toggle("active", state.activeTab === "notes");
    els.viewTask.classList.toggle("active", state.activeTab === "task");

    const qCount = state.quickItems.length;
    els.badgeQuick.textContent = qCount;
    els.badgeQuick.classList.toggle("hidden", qCount === 0);

    const doingCount = state.focusTasks.filter((t) => t.status !== "done").length;
    els.badgeTask.textContent = doingCount;
    els.badgeTask.classList.toggle("hidden", doingCount === 0);

    els.topHeader.classList.remove("hidden");

    if (state.activeTab === "quick") {
      els.hdrTitle.textContent = "Ghi nhanh";
      els.hdrBadge.textContent = qCount + " mục";
      els.hdrBadge.classList.toggle("hidden", qCount === 0);
      els.btnTaskTargetToggle.classList.add("hidden");
      els.composerInput.placeholder = "Tin nhắn";
    } else if (state.activeTab === "notes") {
      els.hdrTitle.textContent = "Ghi chú";
      els.hdrBadge.classList.add("hidden");
      els.btnTaskTargetToggle.classList.add("hidden");
      els.composerInput.placeholder = "Tin nhắn";
    } else {
      els.hdrTitle.textContent = "Trọng tâm hôm nay";
      els.hdrBadge.classList.add("hidden");
      els.btnTaskTargetToggle.classList.remove("hidden");
      els.btnTaskTargetToggle.textContent = state.taskTarget === "focus" ? "Hôm nay" : "Kho chờ";
      els.composerInput.placeholder = "Tin nhắn";
    }
    syncComposerButtons();
  }

  function renderQuickTab() {
    els.quickList.classList.toggle("chat-mode", state.notesDisplayMode === "chat");
    const items = state.quickItems.filter((it) => matchesQuery(it.text, it.children));
    if (items.length === 0) {
      els.quickList.innerHTML = '<div class="empty-box">Chưa có nháp</div>';
      return;
    }

    els.quickList.innerHTML = items.map((it) => {
      const starred = /^★\s*/.test(it.text || "");
      const leadMarker = starred
        ? '<span class="item-lead-star">' + ICONS.starFill + "</span>"
        : '<span class="item-lead-dot"></span>';
      const content = renderRichContentBlock(it.text, true, "qi-text");
      const childrenHtml = (it.children && it.children.length)
        ? '<div class="ne-children">' + it.children.map((c) => '<div class="ne-child">' + renderRichContentBlock(c, false, "ne-child-txt").html + "</div>").join("") + "</div>"
        : "";
      const bmClass = (content.hasBookmark ? " has-bookmark" : "") + (content.bookmarkOnly ? " bookmark-only" : "");
      return (
        '<div class="quick-item' + bmClass + '" data-id="' + it.id + '">' +
          leadMarker +
          '<div class="qi-body" data-edit-quick="' + it.id + '">' +
            content.html +
            childrenHtml +
          "</div>" +
        "</div>"
      );
    }).join("");
  }

  function renderNotesTab() {
    const activeStreams = state.noteStreams.filter((s) => s.group === "active");
    const archiveStreams = state.noteStreams.filter((s) => s.group === "archive");
    const curStream = getActiveStream();

    els.leftColStreams.innerHTML = activeStreams.map((s) => {
      const isOn = curStream && s.id === curStream.id;
      return '<button class="col-stream-btn ' + (isOn ? "active" : "") + '" data-select-stream="' + escHtml(s.id) + '" type="button">' + escHtml(s.title) + "</button>";
    }).join("");

    els.rightColStreams.innerHTML = archiveStreams.map((s) => {
      const isOn = curStream && s.id === curStream.id;
      return '<button class="col-stream-btn ' + (isOn ? "active" : "") + '" data-select-stream="' + escHtml(s.id) + '" type="button">' + escHtml(s.title) + "</button>";
    }).join("");

    if (curStream) {
      els.activeStreamName.textContent = curStream.title;
    }

    els.pinnedTreeBox.classList.toggle("collapsed", state.treeCollapsed);
    els.notesStreamList.classList.toggle("chat-mode", state.notesDisplayMode === "chat");

    if (!curStream) {
      els.notesStreamList.innerHTML = "";
      return;
    }
    const filtered = curStream.items.filter((it) => matchesQuery(it.text, it.children));
    if (filtered.length === 0) {
      els.notesStreamList.innerHTML = '<div class="empty-box">Chưa có ghi chú</div>';
      return;
    }
    els.notesStreamList.innerHTML = filtered.map((it) => {
      const starred = /^★\s*/.test(it.text || "");
      const leadMarker = starred
        ? '<span class="item-lead-star">' + ICONS.starFill + "</span>"
        : '<span class="item-lead-dot"></span>';
      const content = renderRichContentBlock(it.text, true, "ne-text");
      const subHtml = (it.children && it.children.length)
        ? '<div class="ne-children">' + it.children.map((c) => '<div class="ne-child">' + renderRichContentBlock(c, false, "ne-child-txt").html + "</div>").join("") + "</div>"
        : "";
      const bmClass = (content.hasBookmark ? " has-bookmark" : "") + (content.bookmarkOnly ? " bookmark-only" : "");
      return (
        '<div class="note-entry' + bmClass + '" data-entry-id="' + it.id + '" data-stream-id="' + curStream.id + '">' +
          '<div class="ne-lead-row">' +
            leadMarker +
            '<div class="ne-content-col">' + content.html + "</div>" +
          "</div>" +
          subHtml +
        "</div>"
      );
    }).join("");
  }

  function renderTaskTab() {
    const focusFiltered = state.focusTasks.filter((t) =>
      matchesQuery(t.text, (t.subTasks || []).map((s) => s.text))
    );

    if (focusFiltered.length === 0) {
      els.focusTaskList.innerHTML = '<div class="empty-box">Chưa có việc hôm nay</div>';
    } else {
      els.focusTaskList.innerHTML = focusFiltered.map((t) => {
        const hasSubs = Array.isArray(t.subTasks) && t.subTasks.length > 0;
        const isDone = t.status === "done";
        const isCollapsed = !!state.collapsedTaskIds[t.id];

        const leftBtnClass = hasSubs && !isDone
          ? "task-node-btn parent-plus"
          : "task-node-btn" + (isDone ? " checked" : "");
        const leftInner = isDone ? ICONS.check : (hasSubs ? ICONS.plus : "");

        const subTreeHtml = (hasSubs && !isCollapsed)
          ? '<div class="subtask-tree">' +
              t.subTasks.map((st) => (
                '<div class="subtask-row">' +
                  '<button class="task-node-btn ' + (st.done ? "checked" : "") + '" data-toggle-sub="' + escHtml(t.id) + "|" + escHtml(st.id) + '" type="button">' +
                    (st.done ? ICONS.check : "") +
                  "</button>" +
                  '<span class="st-label ' + (st.done ? "done" : "") + '" data-edit-sub="' + escHtml(t.id) + "|" + escHtml(st.id) + '">' +
                    escHtml(st.text) +
                  "</span>" +
                "</div>"
              )).join("") +
            "</div>"
          : "";

        const remBadge = formatReminderBadge(t.remindAt);
        const remHtml = remBadge
          ? '<span class="tc-remind-pill' + (isDone ? " done" : (remBadge.overdue ? " overdue" : "")) + '">' + escHtml(remBadge.label) + "</span>"
          : "";

        return (
          '<div class="task-card" data-task-id="' + escHtml(t.id) + '">' +
            '<div class="tc-top-row">' +
              '<button class="' + leftBtnClass + '" data-task-circle="' + escHtml(t.id) + '" type="button">' + leftInner + "</button>" +
              '<div class="tc-body-col" data-task-menu="' + escHtml(t.id) + '">' +
                '<span class="tc-label ' + (isDone ? "done" : "") + '">' + escHtml(t.text) + "</span>" +
                remHtml +
              "</div>" +
              (hasSubs
                ? '<button class="tc-chevron" data-collapse-task="' + escHtml(t.id) + '" type="button">' + (isCollapsed ? ICONS.chevDown : ICONS.chevUp) + "</button>"
                : '<button class="tc-chevron" data-add-subtask="' + escHtml(t.id) + '" type="button">' + ICONS.plusSm + "</button>") +
            "</div>" +
            subTreeHtml +
          "</div>"
        );
      }).join("");
    }

    const pendingWh = state.warehouseTasks.filter((w) => !w.done && matchesQuery(w.cleanText || w.text));
    els.warehouseHdrLabel.textContent = "Việc chờ (" + pendingWh.length + ")";
    els.warehouseChevron.outerHTML = state.warehouseOpen
      ? '<svg id="warehouseChevron" class="ic-sm" viewBox="0 0 24 24"><polyline points="18 15 12 9 6 15"/></svg>'
      : '<svg id="warehouseChevron" class="ic-sm" viewBox="0 0 24 24"><polyline points="6 9 12 15 18 9"/></svg>';
    els.warehouseChevron = document.getElementById("warehouseChevron");
    els.warehouseTaskList.classList.toggle("hidden", !state.warehouseOpen);

    if (state.warehouseOpen) {
      if (pendingWh.length === 0) {
        els.warehouseTaskList.innerHTML = '<div class="empty-box" style="padding:18px;">Trống</div>';
      } else {
        let lastGrp = "";
        const html = [];
        for (const w of pendingWh) {
          if (w.group && w.group !== "Việc chờ" && w.group !== lastGrp) {
            lastGrp = w.group;
            html.push('<div class="wh-group-title">' + escHtml(w.group) + "</div>");
          }
          const wRem = formatReminderBadge(w.remindAt);
          const wRemHtml = wRem
            ? '<span class="tc-remind-pill' + (wRem.overdue ? " overdue" : "") + '">' + escHtml(wRem.label) + "</span>"
            : "";
          html.push(
            '<div class="task-card" style="padding:11px 12px;margin-bottom:8px;">' +
              '<div class="tc-top-row">' +
                '<button class="task-node-btn" data-check-wh="' + escHtml(w.id) + '" type="button"></button>' +
                '<div class="tc-body-col" data-wh-menu="' + escHtml(w.id) + '">' +
                  '<span class="tc-label" style="font-size:14.5px;">' + escHtml(w.cleanText || w.text) + "</span>" +
                  wRemHtml +
                "</div>" +
                '<button class="promote-btn" data-promote-wh="' + escHtml(w.id) + '" type="button">' + ICONS.arrowUpRight + "<span>Hôm nay</span></button>" +
              "</div>" +
            "</div>"
          );
        }
        els.warehouseTaskList.innerHTML = html.join("");
      }
    }
  }

  function renderAll() {
    renderHeaderAndNav();
    renderQuickTab();
    renderNotesTab();
    renderTaskTab();
  }

  // =========================================================================
  // BOTTOM SHEETS (100% Flat SVG Icons)
  // =========================================================================
  function openSheet(html) {
    els.sheetContent.innerHTML = html;
    els.sheetBackdrop.classList.remove("hidden");
    els.bottomSheet.classList.remove("hidden");
  }

  function closeSheet() {
    els.sheetBackdrop.classList.add("hidden");
    els.bottomSheet.classList.add("hidden");
    els.sheetContent.innerHTML = "";
  }

  els.sheetBackdrop.addEventListener("click", closeSheet);

  function openQuickTriageSheet(itemId) {
    const item = state.quickItems.find((x) => x.id === itemId);
    if (!item) return;
    vibrate(10);

    const activeStreams = state.noteStreams.filter((s) => s.group === "active");
    const archiveStreams = state.noteStreams.filter((s) => s.group === "archive");
    const isStarred = /^★\s*/.test(item.text || "");

    let html = '<div class="sheet-top-bar">' +
      '<button class="sheet-pill-action ' + (isStarred ? "starred" : "") + '" data-sheet-star-quick="' + escHtml(item.id) + '" type="button">' + (isStarred ? ICONS.starFill : ICONS.star) + '<span>' + (isStarred ? "Bỏ sao" : "Gắn sao") + '</span></button>' +
      '<button class="sheet-pill-action" data-sheet-edit-quick="' + escHtml(item.id) + '" type="button">' + ICONS.edit + '<span>Sửa</span></button>' +
      '<button class="sheet-pill-action" data-sheet-copy-quick="' + escHtml(item.id) + '" type="button">' + ICONS.copy + '<span>Chép</span></button>' +
      '<button class="sheet-pill-action danger" data-sheet-del-quick="' + escHtml(item.id) + '" type="button">' + ICONS.trash + '<span>Xóa</span></button>' +
    "</div>";

    html += '<div class="sheet-group-label">Đang giải quyết</div>';
    for (const st of activeStreams) {
      html += '<button class="sheet-row" data-move-to-stream="' + escHtml(st.id) + '" type="button"><span class="sheet-row-left">' + ICONS.target + '<span>' + escHtml(st.title) + "</span></span>" + ICONS.arrowRight + "</button>";
    }
    html += '<div class="sheet-group-label">Kho lưu trữ chung</div>';
    for (const st of archiveStreams) {
      html += '<button class="sheet-row" data-move-to-stream="' + escHtml(st.id) + '" type="button"><span class="sheet-row-left">' + ICONS.inbox + '<span>' + escHtml(st.title) + "</span></span>" + ICONS.arrowRight + "</button>";
    }
    html += '<div class="sheet-group-label">Biến thành Công việc (Task)</div>';
    html += '<button class="sheet-row" data-move-to-task="focus" type="button"><span class="sheet-row-left">' + ICONS.task + '<span>Trọng tâm hôm nay</span></span>' + ICONS.arrowRight + "</button>";
    html += '<button class="sheet-row" data-move-to-task="warehouse" type="button"><span class="sheet-row-left">' + ICONS.inbox + '<span>Kho chờ Task</span></span>' + ICONS.arrowRight + "</button>";

    openSheet(html);

    els.sheetContent.querySelectorAll("[data-move-to-stream]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const targetStream = state.noteStreams.find((s) => s.id === btn.getAttribute("data-move-to-stream"));
        if (!targetStream) return;
        closeSheet();
        vanishQuickItemAndRun(item.id, () => {
          targetStream.items.push({ id: makeId("ni"), time: item.time || nowHHMM(), text: item.text, children: item.children || [] });
          persistQuickNotes();
          showToast("Đã dọn sang " + targetStream.title);
        });
      });
    });

    els.sheetContent.querySelectorAll("[data-move-to-task]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const kind = btn.getAttribute("data-move-to-task");
        closeSheet();
        vanishQuickItemAndRun(item.id, () => {
          persistQuickNotes();
          if (kind === "focus") {
            state.focusTasks.unshift({
              id: makeId("cf"),
              text: item.text,
              why: "",
              status: "doing",
              isCore: false,
              notePath: null,
              subLabel: "",
              subTasks: (item.children || []).map((c) => ({ id: makeId("st"), text: c, done: false }))
            });
            persistDailyAnchor();
            showToast("Đã chuyển vào Trọng tâm hôm nay");
          } else {
            insertIntoWarehouse(item.text);
            showToast("Đã chuyển vào Kho chờ Task");
          }
        });
      });
    });

    els.sheetContent.querySelector("[data-sheet-star-quick]").addEventListener("click", () => {
      closeSheet();
      item.text = /^★\s*/.test(item.text || "") ? item.text.replace(/^★\s*/, "") : ("★ " + (item.text || "").trim());
      persistQuickNotes();
      renderAll();
    });
    els.sheetContent.querySelector("[data-sheet-copy-quick]").addEventListener("click", () => {
      const full = [item.text.replace(/^★\s*/, "")].concat(item.children || []).join("\n- ");
      if (hasNative) window.AndroidBridge.setClipboardText(full);
      closeSheet();
      showToast("Đã sao chép");
    });
    els.sheetContent.querySelector("[data-sheet-edit-quick]").addEventListener("click", () => {
      closeSheet();
      openEditNoteSheet("quick", null, item);
    });
    els.sheetContent.querySelector("[data-sheet-del-quick]").addEventListener("click", () => {
      closeSheet();
      vanishQuickItemAndRun(item.id, () => {
        persistQuickNotes();
        showToast("Đã xóa mục nháp");
      });
    });
  }

  function vanishQuickItemAndRun(itemId, afterRemove) {
    vibrate(14);
    const domRow = els.quickList.querySelector('.quick-item[data-id="' + itemId + '"]');
    if (domRow) domRow.classList.add("vanishing");
    setTimeout(() => {
      state.quickItems = state.quickItems.filter((x) => x.id !== itemId);
      afterRemove();
      renderAll();
    }, 180);
  }

  function insertIntoWarehouse(taskText) {
    const rawBody = String(taskText || "").trim();
    if (!rawBody) return;
    const isStar = /^★\s*/.test(rawBody);
    const noStar = rawBody.replace(/^★\s*/, "").trim();
    const ext = extractMarkdownReminder(noStar);
    state.warehouseTasks.unshift({
      id: makeId("wh"),
      group: "Việc chờ",
      done: false,
      isCore: isStar,
      text: rawBody,
      cleanText: ext.cleanText || noStar,
      remindAt: ext.remindAt || "",
      subTasks: []
    });
    persistQuickTasksMd();
  }

  function openStreamItemMoveSheet(sourceStream, item) {
    if (!sourceStream || !item) return;
    vibrate(10);

    const otherActive = state.noteStreams.filter((s) => s.group === "active" && s.id !== sourceStream.id);
    const otherArchive = state.noteStreams.filter((s) => s.group === "archive" && s.id !== sourceStream.id);
    const isItemStarred = /^★\s*/.test(item.text || "");

    let html = '<div class="sheet-top-bar">' +
      '<button class="sheet-pill-action ' + (isItemStarred ? "starred" : "") + '" data-note-sheet-star="1" type="button">' + (isItemStarred ? ICONS.starFill : ICONS.star) + '<span>' + (isItemStarred ? "Bỏ sao" : "Gắn sao") + '</span></button>' +
      '<button class="sheet-pill-action" data-note-sheet-edit="1" type="button">' + ICONS.edit + '<span>Sửa</span></button>' +
      '<button class="sheet-pill-action" data-note-sheet-copy="1" type="button">' + ICONS.copy + '<span>Chép</span></button>' +
      '<button class="sheet-pill-action danger" data-note-sheet-del="1" type="button">' + ICONS.trash + '<span>Xóa</span></button>' +
    "</div>";

    if (otherActive.length > 0) {
      html += '<div class="sheet-group-label">Đang giải quyết</div>';
      for (const st of otherActive) {
        html += '<button class="sheet-row" data-note-move-stream="' + escHtml(st.id) + '" type="button"><span class="sheet-row-left">' + ICONS.target + '<span>' + escHtml(st.title) + "</span></span>" + ICONS.arrowRight + "</button>";
      }
    }

    if (otherArchive.length > 0) {
      html += '<div class="sheet-group-label">Kho lưu trữ chung</div>';
      for (const st of otherArchive) {
        html += '<button class="sheet-row" data-note-move-stream="' + escHtml(st.id) + '" type="button"><span class="sheet-row-left">' + ICONS.inbox + '<span>' + escHtml(st.title) + "</span></span>" + ICONS.arrowRight + "</button>";
      }
    }

    html += '<div class="sheet-group-label">Chuyển sang Nháp hoặc Công việc</div>';
    html += '<button class="sheet-row" data-note-move-quick="1" type="button"><span class="sheet-row-left">' + ICONS.zapSm + '<span>Đưa về khay Nháp nhanh</span></span>' + ICONS.arrowRight + "</button>";
    html += '<button class="sheet-row" data-note-move-task="focus" type="button"><span class="sheet-row-left">' + ICONS.task + '<span>Trọng tâm hôm nay</span></span>' + ICONS.arrowRight + "</button>";
    html += '<button class="sheet-row" data-note-move-task="warehouse" type="button"><span class="sheet-row-left">' + ICONS.inbox + '<span>Kho chờ Task</span></span>' + ICONS.arrowRight + "</button>";

    openSheet(html);

    function vanishFromStreamAndRun(afterRemove) {
      vibrate(14);
      const domCard = els.notesStreamList.querySelector('.note-entry[data-entry-id="' + item.id + '"]');
      if (domCard) domCard.classList.add("vanishing");
      setTimeout(() => {
        sourceStream.items = sourceStream.items.filter((x) => x.id !== item.id);
        afterRemove();
        renderAll();
      }, 170);
    }

    els.sheetContent.querySelectorAll("[data-note-move-stream]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const targetSt = state.noteStreams.find((s) => s.id === btn.getAttribute("data-note-move-stream"));
        if (!targetSt) return;
        closeSheet();
        vanishFromStreamAndRun(() => {
          targetSt.items.push({ id: makeId("ni"), time: item.time || nowHHMM(), text: item.text, children: item.children || [] });
          persistQuickNotes();
          showToast("Đã chuyển sang " + targetSt.title);
        });
      });
    });

    els.sheetContent.querySelector("[data-note-move-quick]").addEventListener("click", () => {
      closeSheet();
      vanishFromStreamAndRun(() => {
        state.quickItems.push({ id: makeId("ni"), time: item.time || nowHHMM(), text: item.text, children: item.children || [] });
        persistQuickNotes();
        showToast("Đã đưa về khay Nháp");
      });
    });

    els.sheetContent.querySelectorAll("[data-note-move-task]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const kind = btn.getAttribute("data-note-move-task");
        closeSheet();
        vanishFromStreamAndRun(() => {
          persistQuickNotes();
          if (kind === "focus") {
            state.focusTasks.unshift({
              id: makeId("cf"),
              text: item.text,
              why: "",
              status: "doing",
              isCore: false,
              notePath: null,
              subLabel: "",
              subTasks: (item.children || []).map((c) => ({ id: makeId("st"), text: c, done: false }))
            });
            persistDailyAnchor();
            showToast("Đã chuyển vào Trọng tâm hôm nay");
          } else {
            insertIntoWarehouse(item.text);
            showToast("Đã chuyển vào Kho chờ Task");
          }
        });
      });
    });

    els.sheetContent.querySelector("[data-note-sheet-star]").addEventListener("click", () => {
      closeSheet();
      item.text = /^★\s*/.test(item.text || "") ? item.text.replace(/^★\s*/, "") : ("★ " + (item.text || "").trim());
      persistQuickNotes();
      renderAll();
    });
    els.sheetContent.querySelector("[data-note-sheet-edit]").addEventListener("click", () => {
      closeSheet();
      openEditNoteSheet("notes", sourceStream, item);
    });
    els.sheetContent.querySelector("[data-note-sheet-copy]").addEventListener("click", () => {
      const full = [item.text].concat(item.children || []).join("\n- ");
      if (hasNative) window.AndroidBridge.setClipboardText(full);
      closeSheet();
      showToast("Đã sao chép");
    });
    els.sheetContent.querySelector("[data-note-sheet-del]").addEventListener("click", () => {
      closeSheet();
      vanishFromStreamAndRun(() => {
        persistQuickNotes();
        showToast("Đã xóa mục");
      });
    });
  }

  let activeFocusEdit = null;

  function closeFocusEditModal() {
    activeFocusEdit = null;
    els.focusEditOverlay.classList.add("hidden");
    els.focusEditInput.blur();
  }

  function syncFocusTextareaHeight() {
    els.focusEditInput.style.height = "auto";
    els.focusEditInput.style.height = Math.min(260, Math.max(84, els.focusEditInput.scrollHeight)) + "px";
  }

  function syncFocusStarBtn(isStarred) {
    els.btnFocusStar.classList.toggle("active", isStarred);
    els.btnFocusStar.innerHTML = (isStarred ? ICONS.starFill : ICONS.star) + "<span>" + (isStarred ? "Có sao" : "Sao") + "</span>";
  }

  function openEditNoteSheet(scope, stream, item) {
    if (!item) return;
    closeSheet();
    const starredNow = /^★\s*/.test(item.text || "");
    const cleanTxt = (item.text || "").replace(/^★\s*/, "");
    activeFocusEdit = { scope: scope, stream: stream, item: item, starred: starredNow };

    els.focusEditInput.value = cleanTxt;
    syncFocusStarBtn(starredNow);

    if (item.children && item.children.length > 0) {
      els.focusEditChildren.classList.remove("hidden");
      els.focusEditChildren.innerHTML = item.children.map((ch, idx) =>
        '<button class="sheet-row" data-unmerge-idx="' + idx + '" type="button"><span>' + escHtml(ch) + "</span>" + ICONS.arrowUpRight + "</button>"
      ).join("");
      els.focusEditChildren.querySelectorAll("[data-unmerge-idx]").forEach((btn) => {
        btn.addEventListener("click", () => {
          const idx = parseInt(btn.getAttribute("data-unmerge-idx"), 10);
          const [removed] = item.children.splice(idx, 1);
          if (removed) {
            const standalone = { id: makeId("ni"), time: nowHHMM(), text: removed, children: [] };
            if (scope === "quick") {
              const pos = state.quickItems.indexOf(item);
              state.quickItems.splice(pos + 1, 0, standalone);
            } else if (stream) {
              const pos = stream.items.indexOf(item);
              stream.items.splice(pos + 1, 0, standalone);
            }
            persistQuickNotes();
            renderAll();
            openEditNoteSheet(scope, stream, item);
          }
        });
      });
    } else {
      els.focusEditChildren.classList.add("hidden");
      els.focusEditChildren.innerHTML = "";
    }

    els.focusEditOverlay.classList.remove("hidden");
    syncFocusTextareaHeight();
    setTimeout(() => {
      els.focusEditInput.focus();
      const len = els.focusEditInput.value.length;
      try { els.focusEditInput.setSelectionRange(len, len); } catch (_) {}
    }, 35);
  }

  els.focusEditInput.addEventListener("input", syncFocusTextareaHeight);
  els.btnFocusStar.addEventListener("click", () => {
    if (!activeFocusEdit) return;
    activeFocusEdit.starred = !activeFocusEdit.starred;
    vibrate(8);
    syncFocusStarBtn(activeFocusEdit.starred);
  });
  els.btnFocusCancel.addEventListener("click", closeFocusEditModal);
  els.focusEditOverlay.addEventListener("click", (e) => {
    if (e.target === els.focusEditOverlay) closeFocusEditModal();
  });
  els.btnFocusSave.addEventListener("click", () => {
    if (!activeFocusEdit) return;
    const val = els.focusEditInput.value.trim();
    if (val) {
      activeFocusEdit.item.text = activeFocusEdit.starred ? ("★ " + val) : val;
      persistQuickNotes();
      renderAll();
    }
    vibrate(10);
    closeFocusEditModal();
  });

  function openAddStreamSheet(defaultGroup) {
    const grp = defaultGroup === "archive" ? "archive" : "active";
    const label = grp === "active" ? "Thêm vấn đề Đang giải quyết" : "Thêm mục Kho lưu trữ";
    openSheet(
      '<div class="sheet-title">' + label + "</div>" +
      '<input id="newStreamTitleInput" class="modal-input" type="text" placeholder="Nhập tên mạch (tự sinh Heading ## trong file)..." />' +
      '<button id="btnConfirmAddStream" class="primary-btn" type="button">Tạo ngay</button>'
    );
    const inp = document.getElementById("newStreamTitleInput");
    setTimeout(() => inp && inp.focus(), 60);
    document.getElementById("btnConfirmAddStream").addEventListener("click", () => {
      const name = inp.value.trim().replace(/^#+\s*/, "");
      if (!name) return;
      const newSt = { id: "st_" + grp + "_" + name + "_" + Date.now(), group: grp, title: name, items: [] };
      state.noteStreams.push(newSt);
      state.activeStreamId = newSt.id;
      localStorage.setItem("oq_active_stream", newSt.id);
      persistQuickNotes();
      closeSheet();
      renderAll();
      showToast("Đã tạo ## " + name);
    });
  }

  function buildSheetRemindHtml(currentRemindAt) {
    const badge = formatReminderBadge(currentRemindAt);
    const activeHtml = badge
      ? '<div class="sheet-remind-active"><span class="tc-remind-pill' + (badge.overdue ? " overdue" : "") + '">' + escHtml(badge.label) + '</span><button id="btnClearTaskRemind" class="remind-clear-btn" type="button">✕ Xóa</button></div>'
      : "";
    return (
      '<div class="sheet-remind-box">' +
        activeHtml +
        '<div class="sheet-remind-presets">' +
          '<button class="remind-chip" data-rem-preset="in_1h" type="button">+1 giờ</button>' +
          '<button class="remind-chip" data-rem-preset="tonight_21" type="button">Tối 21:00</button>' +
          '<button class="remind-chip" data-rem-preset="tomorrow_9" type="button">Mai 09:00</button>' +
          '<button class="remind-chip accent" data-rem-custom="1" type="button">⏰ Chọn giờ</button>' +
        "</div>" +
      "</div>"
    );
  }

  function bindSheetRemindActions(currentRemindAt, onApplyRemind) {
    els.sheetContent.querySelectorAll("[data-rem-preset]").forEach((btn) => {
      btn.addEventListener("click", () => {
        ensureReminderPermissionSafe();
        const nextVal = buildReminderPreset(btn.getAttribute("data-rem-preset"));
        onApplyRemind(nextVal);
      });
    });
    const customBtn = els.sheetContent.querySelector("[data-rem-custom]");
    if (customBtn) {
      customBtn.addEventListener("click", () => {
        requestNativeReminderPicker(currentRemindAt, (picked) => {
          if (picked) onApplyRemind(picked);
        });
      });
    }
    const clearBtn = document.getElementById("btnClearTaskRemind");
    if (clearBtn) {
      clearBtn.addEventListener("click", () => onApplyRemind(""));
    }
  }

  function openTaskActionSheet(task) {
    let html = '<div class="sheet-title">Tùy chỉnh công việc</div>';
    html += '<input id="editTaskTitleInp" class="modal-input" type="text" value="' + escHtml(task.text) + '" />';
    html += buildSheetRemindHtml(task.remindAt || "");
    html += '<button id="btnSaveTaskTitle" class="primary-btn" style="margin-bottom:10px;" type="button">Lưu tên việc</button>';
    html += '<button id="btnSheetAddSub" class="sheet-row" type="button"><span class="sheet-row-left">' + ICONS.plusSm + '<span>Thêm việc con vào nhánh cây</span></span>' + ICONS.arrowRight + "</button>";
    html += '<button id="btnSheetToggleDone" class="sheet-row" type="button"><span class="sheet-row-left">' + ICONS.task + "<span>" +
      (task.status === "done" ? "Đánh dấu chưa xong" : "Hoàn thành công việc này") +
      "</span></span></button>";
    html += '<button id="btnSheetMoveToWh" class="sheet-row" type="button"><span class="sheet-row-left">' + ICONS.inbox + '<span>Hạ xuống Kho chờ Task</span></span>' + ICONS.arrowRight + "</button>";
    html += '<button id="btnSheetDeleteTask" class="sheet-row danger" type="button"><span class="sheet-row-left">' + ICONS.trash + '<span>Xóa công việc</span></span></button>';

    openSheet(html);

    bindSheetRemindActions(task.remindAt || "", (nextVal) => {
      vibrate(10);
      task.remindAt = nextVal;
      persistDailyAnchor();
      renderAll();
      closeSheet();
      const b = formatReminderBadge(nextVal);
      showToast(b ? "Đã hẹn nhắc " + b.label : "Đã xóa hẹn giờ");
    });

    document.getElementById("btnSaveTaskTitle").addEventListener("click", () => {
      const v = document.getElementById("editTaskTitleInp").value.trim();
      if (v) { task.text = v; persistDailyAnchor(); renderAll(); }
      closeSheet();
    });
    document.getElementById("btnSheetAddSub").addEventListener("click", () => openAddSubtaskSheet(task));
    document.getElementById("btnSheetToggleDone").addEventListener("click", () => {
      task.status = task.status === "done" ? "doing" : "done";
      persistDailyAnchor(); closeSheet(); renderAll();
    });
    document.getElementById("btnSheetMoveToWh").addEventListener("click", () => {
      state.focusTasks = state.focusTasks.filter((x) => x.id !== task.id);
      persistDailyAnchor();
      insertIntoWarehouse(applyMarkdownReminder(task.text, task.remindAt));
      closeSheet(); renderAll();
      showToast("Đã chuyển xuống Kho chờ");
    });
    document.getElementById("btnSheetDeleteTask").addEventListener("click", () => {
      state.focusTasks = state.focusTasks.filter((x) => x.id !== task.id);
      persistDailyAnchor(); closeSheet(); renderAll();
    });
  }

  function openWarehouseTaskSheet(wTask) {
    let html = '<div class="sheet-title">' + escHtml(wTask.group || "Việc chờ") + "</div>";
    html += '<input id="editWhTitleInp" class="modal-input" type="text" value="' + escHtml(wTask.cleanText || wTask.text) + '" />';
    html += buildSheetRemindHtml(wTask.remindAt || "");
    html += '<button id="btnSaveWhTitle" class="primary-btn" style="margin-bottom:10px;" type="button">Lưu thay đổi</button>';
    html += '<button id="btnWhPromoteSheet" class="sheet-row" type="button"><span class="sheet-row-left">' + ICONS.arrowUpRight + '<span>Đưa lên Hôm nay</span></span>' + ICONS.arrowRight + "</button>";
    html += '<button id="btnWhDeleteSheet" class="sheet-row danger" type="button"><span class="sheet-row-left">' + ICONS.trash + '<span>Xóa công việc</span></span></button>';

    openSheet(html);

    function commitWarehouseEdit(newTitle, newRemind) {
      const bodyClean = String(newTitle || "").replace(/^★\s*/, "").trim();
      if (bodyClean) wTask.cleanText = bodyClean;
      wTask.remindAt = normalizeRemindAt(newRemind);
      wTask.text = applyMarkdownReminder(wTask.cleanText, wTask.remindAt);
      persistQuickTasksMd();
      renderAll();
    }

    bindSheetRemindActions(wTask.remindAt || "", (nextVal) => {
      vibrate(10);
      const curInp = document.getElementById("editWhTitleInp");
      const titleNow = curInp ? curInp.value.trim() : (wTask.cleanText || wTask.text);
      commitWarehouseEdit(titleNow || wTask.cleanText || wTask.text, nextVal);
      closeSheet();
      const b = formatReminderBadge(nextVal);
      showToast(b ? "Đã hẹn nhắc " + b.label : "Đã xóa hẹn giờ");
    });

    document.getElementById("btnSaveWhTitle").addEventListener("click", () => {
      const v = document.getElementById("editWhTitleInp").value.trim();
      if (v) commitWarehouseEdit(v, wTask.remindAt || "");
      closeSheet();
    });
    document.getElementById("btnWhPromoteSheet").addEventListener("click", () => {
      state.warehouseTasks = state.warehouseTasks.filter((x) => x.id !== wTask.id);
      state.focusTasks.unshift({
        id: makeId("cf"), text: wTask.cleanText || wTask.text, why: "", status: "doing",
        isCore: !!wTask.isCore, notePath: null, subLabel: "", subTasks: wTask.subTasks || [], remindAt: wTask.remindAt || ""
      });
      persistQuickTasksMd(); closeSheet(); renderAll();
      showToast("Đã đưa lên Hôm nay");
    });
    document.getElementById("btnWhDeleteSheet").addEventListener("click", () => {
      state.warehouseTasks = state.warehouseTasks.filter((x) => x.id !== wTask.id);
      persistQuickTasksMd();
      renderAll();
      closeSheet();
    });
  }

  function openAddSubtaskSheet(parentTask) {
    openSheet(
      '<div class="sheet-title">Thêm việc con</div>' +
      '<input id="newSubtaskInp" class="modal-input" type="text" placeholder="Nhập tên việc con..." />' +
      '<button id="btnConfirmSubtask" class="primary-btn" type="button">Thêm vào nhánh</button>'
    );
    const inp = document.getElementById("newSubtaskInp");
    setTimeout(() => inp && inp.focus(), 60);
    document.getElementById("btnConfirmSubtask").addEventListener("click", () => {
      const v = inp.value.trim();
      if (!v) return;
      parentTask.subTasks = parentTask.subTasks || [];
      parentTask.subTasks.push({ id: makeId("st"), text: v, done: false });
      delete state.collapsedTaskIds[parentTask.id];
      persistDailyAnchor(); closeSheet(); renderAll();
    });
  }

  function openSettingsSheet() {
    const cleanVaultName = String(state.vaultLabel || "").replace(/^\.+/, "").trim();
    const statusText = state.vaultConnected ? (cleanVaultName || "Vault đã nối") : "Chọn thư mục Vault";
    const nextModeLabel = state.notesDisplayMode === "chat" ? "Đổi sang Thẻ khối" : "Đổi sang Bong bóng";
    const nextModeIcon = state.notesDisplayMode === "chat" ? ICONS.block : ICONS.chat;
    openSheet(
      '<div class="sheet-title"><span>Tùy chọn</span><span class="sheet-ver">v' + escHtml(state.appVersion) + "</span></div>" +
      '<div class="sheet-grid-3">' +
        '<button class="swatch-btn ' + (state.theme === "slate" ? "active" : "") + '" data-pick-theme="slate" type="button"><span class="swatch-dot" style="background:#1f222b;"></span><span>Xám khói</span></button>' +
        '<button class="swatch-btn ' + (state.theme === "obsidian" ? "active" : "") + '" data-pick-theme="obsidian" type="button"><span class="swatch-dot" style="background:#7c3aed;"></span><span>Obsidian</span></button>' +
        '<button class="swatch-btn ' + (state.theme === "light" ? "active" : "") + '" data-pick-theme="light" type="button"><span class="swatch-dot" style="background:#f8fafc;border-color:#cbd5e1;"></span><span>Sáng</span></button>' +
      "</div>" +
      '<button id="btnSwitchNotesModeSheet" class="sheet-row" style="margin-bottom:8px;justify-content:center;" type="button"><span class="sheet-row-left">' + nextModeIcon + "<span>" + nextModeLabel + "</span></span></button>" +
      '<button id="btnPickVaultSheet" class="sheet-row" style="margin-bottom:8px;" type="button">' +
        '<span class="sheet-row-left" style="flex:1;"><span style="display:flex;">' + ICONS.folder + '</span><span class="sheet-one-line">' + escHtml(statusText) + '</span></span>' +
        '<span style="color:var(--accent);font-weight:600;white-space:nowrap;">' + (state.vaultConnected ? "Đổi" : "Chọn") + "</span>" +
      "</button>" +
      '<div class="sheet-grid-2" style="margin-bottom:0;">' +
        '<button id="btnCheckOtaSheet" class="sheet-row" type="button"><span class="sheet-row-left">' + ICONS.refresh + '<span>Cập nhật</span></span></button>' +
        '<button id="btnReloadVaultSheet" class="sheet-row" type="button"><span class="sheet-row-left">' + ICONS.refresh + '<span>Làm mới</span></span></button>' +
      "</div>"
    );

    els.sheetContent.querySelectorAll("[data-pick-theme]").forEach((btn) => {
      btn.addEventListener("click", () => {
        state.theme = btn.getAttribute("data-pick-theme");
        localStorage.setItem("oq_theme", state.theme);
        applyTheme();
        closeSheet();
      });
    });
    document.getElementById("btnSwitchNotesModeSheet").addEventListener("click", () => {
      state.notesDisplayMode = state.notesDisplayMode === "chat" ? "block" : "chat";
      localStorage.setItem("oq_notes_display", state.notesDisplayMode);
      closeSheet();
      renderAll();
    });
    document.getElementById("btnPickVaultSheet").addEventListener("click", () => {
      closeSheet();
      if (hasNative) window.AndroidBridge.pickVaultFolder();
    });
    const grantBtn = document.getElementById("btnGrantAllFilesSheet");
    if (grantBtn) {
      grantBtn.addEventListener("click", () => {
        closeSheet();
        if (hasNative) window.AndroidBridge.requestAllFilesAccess();
      });
    }
    document.getElementById("btnCheckOtaSheet").addEventListener("click", () => {
      closeSheet();
      if (hasNative) window.AndroidBridge.startNativeUpdate();
    });
    document.getElementById("btnReloadVaultSheet").addEventListener("click", () => {
      closeSheet();
      loadVaultState();
      showToast("Đã làm mới dữ liệu từ Vault");
    });
  }

  // =========================================================================
  // COMPOSER & KEYBOARD AUTO-COLLAPSE
  // =========================================================================
  function submitComposer() {
    const raw = els.composerInput.value.trim();
    if (!raw) return;
    vibrate(10);

    if (state.activeTab === "quick") {
      state.quickItems.push({ id: makeId("ni"), time: nowHHMM(), text: raw, children: [] });
      persistQuickNotes();
      scrollActiveListToBottom();
    } else if (state.activeTab === "notes") {
      const st = getActiveStream();
      if (!st) return;
      st.items.push({ id: makeId("ni"), time: nowHHMM(), text: raw, children: [] });
      persistQuickNotes();
      scrollActiveListToBottom();
    } else if (state.activeTab === "task") {
      if (state.taskTarget === "focus") {
        state.focusTasks.unshift({
          id: makeId("cf"), text: raw, why: "", status: "doing",
          isCore: false, notePath: null, subLabel: "", subTasks: []
        });
        persistDailyAnchor();
      } else {
        insertIntoWarehouse(raw);
      }
    }

    els.composerInput.value = "";
    els.composerInput.style.height = "auto";
    els.tagSuggestBar.classList.add("hidden");
    syncComposerButtons();
    renderAll();
  }

  els.btnSend.addEventListener("click", submitComposer);
  els.composerInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      submitComposer();
    }
  });

  els.composerInput.addEventListener("input", () => {
    els.composerInput.style.height = "auto";
    els.composerInput.style.height = Math.min(els.composerInput.scrollHeight, 108) + "px";
    syncComposerButtons();

    const val = els.composerInput.value;
    const m = val.match(/(^|\s)(#[\p{L}\p{N}_/-]*)$/u);
    if (m) {
      const q = m[2].toLowerCase();
      const matches = state.vaultTags.filter((t) => t.toLowerCase().startsWith(q)).slice(0, 8);
      if (matches.length > 0) {
        els.tagSuggestBar.innerHTML = matches.map((t) =>
          '<button class="tag-chip-btn" data-pick-tag="' + escHtml(t) + '" type="button">' + escHtml(t) + "</button>"
        ).join("");
        els.tagSuggestBar.classList.remove("hidden");
        return;
      }
    }
    els.tagSuggestBar.classList.add("hidden");
  });

  els.tagSuggestBar.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-pick-tag]");
    if (!btn) return;
    const tag = btn.getAttribute("data-pick-tag");
    els.composerInput.value = els.composerInput.value.replace(/(#[\p{L}\p{N}_/-]*)$/u, tag + " ");
    els.tagSuggestBar.classList.add("hidden");
    syncComposerButtons();
    els.composerInput.focus();
  });

  function setTreeCollapsed(collapsed) {
    if (state.treeCollapsed === collapsed) return;
    state.treeCollapsed = collapsed;
    els.pinnedTreeBox.classList.toggle("collapsed", collapsed);
    vibrate(8);
  }

  // Measure actual viewport height so dismissing Android keyboard ALWAYS restores the 3 bottom tabs immediately!
  let maxViewportHeight = window.visualViewport ? window.visualViewport.height : window.innerHeight;
  let prevKbVisible = false;

  function updateKeyboardOpenState() {
    const curH = window.visualViewport ? window.visualViewport.height : window.innerHeight;
    if (curH > maxViewportHeight) maxViewportHeight = curH;
    const kbVisible = (maxViewportHeight - curH) > 115;
    document.body.classList.toggle("keyboard-open", kbVisible);
    if (kbVisible && state.activeTab === "notes" && !state.treeCollapsed) {
      state.treeCollapsed = true;
      els.pinnedTreeBox.classList.add("collapsed");
    }
    if (kbVisible && !prevKbVisible) {
      scrollActiveListToBottom();
    }
    prevKbVisible = kbVisible;
  }

  window.addEventListener("resize", updateKeyboardOpenState);
  if (window.visualViewport) {
    window.visualViewport.addEventListener("resize", updateKeyboardOpenState);
  }
  els.composerInput.addEventListener("blur", () => {
    setTimeout(updateKeyboardOpenState, 80);
  });

  // =========================================================================
  // NATURAL SWIPE UP / SWIPE DOWN GESTURE FOR 2-COLUMN TOP BLOCK
  // =========================================================================
  function bindVerticalDrawerSwipe(el, opts) {
    if (!el) return;
    let sy = 0;
    let sx = 0;
    let handled = false;

    el.addEventListener("touchstart", (e) => {
      if (e.touches.length !== 1 || dragState) return;
      sy = e.touches[0].clientY;
      sx = e.touches[0].clientX;
      handled = false;
    }, { passive: true });

    el.addEventListener("touchmove", (e) => {
      if (handled || !sy || dragState) return;
      const dy = e.touches[0].clientY - sy;
      const dx = e.touches[0].clientX - sx;
      if (Math.abs(dy) < (opts.threshold || 22) || Math.abs(dx) > Math.abs(dy) * 0.9) return;

      if (dy < 0 && !state.treeCollapsed && opts.allowSwipeUp) {
        handled = true;
        setTreeCollapsed(true);
      } else if (dy > 0 && state.treeCollapsed && opts.allowSwipeDown && (!opts.requireAtTop || el.scrollTop <= 2)) {
        handled = true;
        setTreeCollapsed(false);
      }
    }, { passive: true });

    el.addEventListener("touchend", () => { sy = 0; handled = false; }, { passive: true });
  }

  // Swipe up/down on pill bar, merged title header, 2-col box, and note stream area!
  bindVerticalDrawerSwipe(els.drawerHandleZone, { threshold: 14, allowSwipeUp: true, allowSwipeDown: true });
  bindVerticalDrawerSwipe(els.notesStreamHeader, { threshold: 16, allowSwipeUp: true, allowSwipeDown: true });
  bindVerticalDrawerSwipe(els.pinnedTreeBox, { threshold: 26, allowSwipeUp: true, allowSwipeDown: false });
  bindVerticalDrawerSwipe(els.notesStreamList, { threshold: 26, allowSwipeUp: true, allowSwipeDown: true, requireAtTop: true });

  els.drawerHandleZone.addEventListener("click", () => {
    setTreeCollapsed(!state.treeCollapsed);
  });

  // =========================================================================
  // LONG-PRESS FOR STAR + BOTTOM SETTINGS (AND HOLD-MOVE FOR MERGE/REORDER)
  // =========================================================================
  let dragState = null;
  let suppressClickUntil = 0;

  els.notesStreamList.addEventListener("contextmenu", (e) => e.preventDefault());
  els.quickList.addEventListener("contextmenu", (e) => e.preventDefault());

  function setupQuickLongPress(container) {
    let pressTimer = null;
    let startX = 0;
    let startY = 0;
    let heldEl = null;

    container.addEventListener("touchstart", (e) => {
      if (e.touches.length !== 1) return;
      const card = e.target.closest(".quick-item");
      if (!card) return;
      startX = e.touches[0].clientX;
      startY = e.touches[0].clientY;
      heldEl = card;
      const itemId = card.getAttribute("data-id");

      pressTimer = setTimeout(() => {
        pressTimer = null;
        suppressClickUntil = Date.now() + 700;
        card.classList.add("holding-focus");
        openQuickTriageSheet(itemId);
      }, 320);
    }, { passive: true });

    container.addEventListener("touchmove", (e) => {
      if (!pressTimer) return;
      const dx = Math.abs(e.touches[0].clientX - startX);
      const dy = Math.abs(e.touches[0].clientY - startY);
      if (dx > 10 || dy > 10) {
        clearTimeout(pressTimer);
        pressTimer = null;
      }
    }, { passive: true });

    const endQuickHold = () => {
      if (pressTimer) { clearTimeout(pressTimer); pressTimer = null; }
      if (heldEl) { heldEl.classList.remove("holding-focus"); heldEl = null; }
    };
    container.addEventListener("touchend", endQuickHold);
    container.addEventListener("touchcancel", endQuickHold);
  }

  function setupLongPressMergeDrag(container) {
    let pressTimer = null;
    let holdCandidate = null;
    let startX = 0;
    let startY = 0;

    container.addEventListener("touchstart", (e) => {
      if (e.touches.length !== 1) return;
      const card = e.target.closest(".note-entry");
      if (!card) return;
      startX = e.touches[0].clientX;
      startY = e.touches[0].clientY;
      const entryId = card.getAttribute("data-entry-id");
      const streamId = card.getAttribute("data-stream-id");

      pressTimer = setTimeout(() => {
        pressTimer = null;
        const stream = state.noteStreams.find((s) => s.id === streamId);
        const item = stream ? stream.items.find((x) => x.id === entryId) : null;
        if (!stream || !item) return;

        suppressClickUntil = Date.now() + 700;
        card.classList.add("holding-focus");
        holdCandidate = { stream, item, card };
        openStreamItemMoveSheet(stream, item);
      }, 320);
    }, { passive: true });

    container.addEventListener("touchmove", (e) => {
      const touch = e.touches[0];
      const dx = Math.abs(touch.clientX - startX);
      const dy = Math.abs(touch.clientY - startY);

      if (!dragState) {
        if (pressTimer && (dx > 10 || dy > 10)) {
          clearTimeout(pressTimer);
          pressTimer = null;
          return;
        }
        if (holdCandidate && (dx > 22 || dy > 22)) {
          const { stream, item, card } = holdCandidate;
          holdCandidate = null;
          closeSheet();
          card.classList.remove("holding-focus");
          card.classList.add("dragging-source");
          const ghost = document.createElement("div");
          ghost.className = "drag-ghost";
          ghost.textContent = item.text.replace(/^★\s*/, "");
          ghost.style.left = Math.max(12, touch.clientX - 90) + "px";
          ghost.style.top = (touch.clientY - 26) + "px";
          document.body.appendChild(ghost);
          dragState = { stream, item, sourceEl: card, ghostEl: ghost, targetEl: null, dropAction: null };
        }
        if (!dragState) return;
      }

      e.preventDefault();
      dragState.ghostEl.style.left = Math.max(12, Math.min(window.innerWidth - 220, touch.clientX - 90)) + "px";
      dragState.ghostEl.style.top = (touch.clientY - 28) + "px";

      document.querySelectorAll(".merge-target, .reorder-above, .reorder-below").forEach((el) => {
        el.classList.remove("merge-target", "reorder-above", "reorder-below");
      });
      dragState.ghostEl.style.display = "none";
      const under = document.elementFromPoint(touch.clientX, touch.clientY);
      dragState.ghostEl.style.display = "";

      const targetCard = under ? under.closest(".note-entry") : null;
      if (!targetCard || targetCard === dragState.sourceEl || targetCard.getAttribute("data-stream-id") !== dragState.stream.id) {
        dragState.targetEl = null;
        dragState.dropAction = null;
        return;
      }

      const rect = targetCard.getBoundingClientRect();
      const relY = (touch.clientY - rect.top) / Math.max(1, rect.height);
      dragState.targetEl = targetCard;
      if (relY < 0.24) { dragState.dropAction = "before"; targetCard.classList.add("reorder-above"); }
      else if (relY > 0.76) { dragState.dropAction = "after"; targetCard.classList.add("reorder-below"); }
      else { dragState.dropAction = "merge"; targetCard.classList.add("merge-target"); }
    }, { passive: false });

    const finishTouch = () => {
      if (pressTimer) { clearTimeout(pressTimer); pressTimer = null; }
      if (holdCandidate) {
        holdCandidate.card.classList.remove("holding-focus");
        holdCandidate = null;
      }
      if (!dragState) return;
      const { stream, item, targetEl, dropAction, ghostEl, sourceEl } = dragState;
      dragState = null;
      sourceEl.classList.remove("dragging-source");
      ghostEl.remove();
      document.querySelectorAll(".merge-target, .reorder-above, .reorder-below").forEach((el) => {
        el.classList.remove("merge-target", "reorder-above", "reorder-below");
      });

      if (targetEl && dropAction) {
        const targetItem = stream.items.find((x) => x.id === targetEl.getAttribute("data-entry-id"));
        if (targetItem && targetItem.id !== item.id) {
          vibrate(16);
          const srcIdx = stream.items.indexOf(item);
          if (srcIdx >= 0) stream.items.splice(srcIdx, 1);
          if (dropAction === "merge") {
            targetItem.children = targetItem.children || [];
            targetItem.children.push(item.text);
            if (item.children && item.children.length) targetItem.children.push(...item.children);
            showToast("Đã gộp chung vào 1 khối");
          } else {
            const tgtIdx = stream.items.indexOf(targetItem);
            stream.items.splice(dropAction === "before" ? tgtIdx : tgtIdx + 1, 0, item);
          }
          persistQuickNotes();
          renderNotesTab();
        }
      }
    };

    container.addEventListener("touchend", finishTouch);
    container.addEventListener("touchcancel", finishTouch);
  }

  setupQuickLongPress(els.quickList);
  setupLongPressMergeDrag(els.notesStreamList);

  // =========================================================================
  // EVENTS & NAVIGATION
  // =========================================================================
  els.bottomBar.addEventListener("click", (e) => {
    const btn = e.target.closest(".nav-tab");
    if (btn) switchTab(btn.dataset.tab);
  });

  function switchTab(tab) {
    if (!["quick", "notes", "task"].includes(tab)) return;
    state.activeTab = tab;
    vibrate(8);
    renderAll();
    scrollActiveListToBottom();
  }

  let swipeX = 0;
  let swipeY = 0;
  document.getElementById("viewport").addEventListener("touchstart", (e) => {
    if (e.touches.length !== 1 || dragState) return;
    swipeX = e.touches[0].clientX;
    swipeY = e.touches[0].clientY;
  }, { passive: true });

  document.getElementById("viewport").addEventListener("touchend", (e) => {
    if (dragState || !swipeX) return;
    const dx = e.changedTouches[0].clientX - swipeX;
    const dy = e.changedTouches[0].clientY - swipeY;
    swipeX = 0;
    if (Math.abs(dx) > 78 && Math.abs(dx) > Math.abs(dy) * 1.8) {
      const order = ["quick", "notes", "task"];
      const idx = order.indexOf(state.activeTab);
      if (dx < 0 && idx < order.length - 1) switchTab(order[idx + 1]);
      else if (dx > 0 && idx > 0) switchTab(order[idx - 1]);
    }
  }, { passive: true });

  function toggleSearchAction() {
    state.searchOpen = !state.searchOpen;
    els.searchBarWrap.classList.toggle("hidden", !state.searchOpen);
    if (state.searchOpen) els.searchInput.focus();
    else { state.searchQuery = ""; els.searchInput.value = ""; renderAll(); }
  }

  els.btnSearchToggle.addEventListener("click", toggleSearchAction);
  els.searchInput.addEventListener("input", () => {
    state.searchQuery = els.searchInput.value.trim();
    renderAll();
  });
  els.btnSearchClear.addEventListener("click", () => {
    state.searchQuery = "";
    els.searchInput.value = "";
    state.searchOpen = false;
    els.searchBarWrap.classList.add("hidden");
    renderAll();
  });
  els.btnSettings.addEventListener("click", openSettingsSheet);

  els.btnConnectVault.addEventListener("click", () => {
    if (!hasNative) return;
    window.AndroidBridge.pickVaultFolder();
  });

  els.btnTaskTargetToggle.addEventListener("click", () => {
    state.taskTarget = state.taskTarget === "focus" ? "warehouse" : "focus";
    vibrate(8);
    renderHeaderAndNav();
  });

  els.btnVoice.addEventListener("click", () => {
    if (hasNative && window.AndroidBridge.startVoiceInput) window.AndroidBridge.startVoiceInput("vi-VN");
  });

  els.quickList.addEventListener("click", (e) => {
    if (Date.now() < suppressClickUntil) return;
    const bmCard = e.target.closest(".notion-bookmark-card");
    if (bmCard) {
      e.stopPropagation();
      openBookmarkUrl(bmCard.getAttribute("data-open-url"));
      return;
    }
    const card = e.target.closest(".quick-item");
    if (card) {
      const item = state.quickItems.find((x) => x.id === card.getAttribute("data-id"));
      if (item) openEditNoteSheet("quick", null, item);
    }
  });

  els.viewNotes.addEventListener("click", (e) => {
    if (dragState || Date.now() < suppressClickUntil) return;
    const addStreamBtn = e.target.closest("[data-add-stream]");
    if (addStreamBtn) { openAddStreamSheet(addStreamBtn.getAttribute("data-add-stream")); return; }
    const selRow = e.target.closest("[data-select-stream]");
    if (selRow) {
      state.activeStreamId = selRow.getAttribute("data-select-stream");
      localStorage.setItem("oq_active_stream", state.activeStreamId);
      vibrate(8);
      renderAll();
      scrollActiveListToBottom();
      return;
    }
    const bmCard = e.target.closest(".notion-bookmark-card");
    if (bmCard) {
      e.stopPropagation();
      openBookmarkUrl(bmCard.getAttribute("data-open-url"));
      return;
    }
    const entryEl = e.target.closest(".note-entry");
    if (entryEl) {
      const st = state.noteStreams.find((s) => s.id === entryEl.getAttribute("data-stream-id"));
      const item = st ? st.items.find((x) => x.id === entryEl.getAttribute("data-entry-id")) : null;
      if (st && item) openEditNoteSheet("notes", st, item);
    }
  });

  els.viewTask.addEventListener("click", (e) => {
    const circleBtn = e.target.closest("[data-task-circle]");
    if (circleBtn) {
      const t = state.focusTasks.find((x) => x.id === circleBtn.getAttribute("data-task-circle"));
      if (!t) return;
      vibrate(12);
      if (t.subTasks && t.subTasks.length > 0 && t.status !== "done") openAddSubtaskSheet(t);
      else { t.status = t.status === "done" ? "doing" : "done"; persistDailyAnchor(); renderAll(); }
      return;
    }
    const collapseBtn = e.target.closest("[data-collapse-task]");
    if (collapseBtn) {
      const id = collapseBtn.getAttribute("data-collapse-task");
      state.collapsedTaskIds[id] = !state.collapsedTaskIds[id];
      renderTaskTab();
      return;
    }
    const addSubBtn = e.target.closest("[data-add-subtask]");
    if (addSubBtn) {
      const t = state.focusTasks.find((x) => x.id === addSubBtn.getAttribute("data-add-subtask"));
      if (t) openAddSubtaskSheet(t);
      return;
    }
    const toggleSubBtn = e.target.closest("[data-toggle-sub]");
    if (toggleSubBtn) {
      const [tid, sid] = toggleSubBtn.getAttribute("data-toggle-sub").split("|");
      const t = state.focusTasks.find((x) => x.id === tid);
      const st = t && t.subTasks ? t.subTasks.find((s) => s.id === sid) : null;
      if (st) { vibrate(12); st.done = !st.done; persistDailyAnchor(); renderAll(); }
      return;
    }
    const editSubEl = e.target.closest("[data-edit-sub]");
    if (editSubEl) {
      const [tid, sid] = editSubEl.getAttribute("data-edit-sub").split("|");
      const t = state.focusTasks.find((x) => x.id === tid);
      const st = t && t.subTasks ? t.subTasks.find((s) => s.id === sid) : null;
      if (t && st) {
        openSheet(
          '<div class="sheet-title">Sửa việc con</div>' +
          '<input id="subEditInp" class="modal-input" type="text" value="' + escHtml(st.text) + '" />' +
          '<button id="btnSaveSub" class="primary-btn" style="margin-bottom:10px;" type="button">Lưu</button>' +
          '<button id="btnDelSub" class="sheet-row danger" type="button"><span class="sheet-row-left">' + ICONS.trash + '<span>Xóa việc con</span></span></button>'
        );
        document.getElementById("btnSaveSub").addEventListener("click", () => {
          const v = document.getElementById("subEditInp").value.trim();
          if (v) st.text = v;
          persistDailyAnchor(); closeSheet(); renderAll();
        });
        document.getElementById("btnDelSub").addEventListener("click", () => {
          t.subTasks = t.subTasks.filter((x) => x.id !== sid);
          persistDailyAnchor(); closeSheet(); renderAll();
        });
      }
      return;
    }
    const menuLabel = e.target.closest("[data-task-menu]");
    if (menuLabel) {
      const t = state.focusTasks.find((x) => x.id === menuLabel.getAttribute("data-task-menu"));
      if (t) openTaskActionSheet(t);
      return;
    }
    const whHdr = e.target.closest("#btnToggleWarehouse");
    if (whHdr) { state.warehouseOpen = !state.warehouseOpen; renderTaskTab(); return; }

    const checkWh = e.target.closest("[data-check-wh]");
    if (checkWh) {
      const whId = checkWh.getAttribute("data-check-wh");
      const wTask = state.warehouseTasks.find((w) => w.id === whId);
      if (wTask) {
        vibrate(12);
        wTask.done = true;
        persistQuickTasksMd(); renderAll();
      }
      return;
    }
    const whMenu = e.target.closest("[data-wh-menu]");
    if (whMenu) {
      const whId = whMenu.getAttribute("data-wh-menu");
      const wTask = state.warehouseTasks.find((w) => w.id === whId);
      if (wTask) openWarehouseTaskSheet(wTask);
      return;
    }
    const promWh = e.target.closest("[data-promote-wh]");
    if (promWh) {
      const whId = promWh.getAttribute("data-promote-wh");
      const wTask = state.warehouseTasks.find((w) => w.id === whId);
      if (wTask) {
        vibrate(14);
        state.warehouseTasks = state.warehouseTasks.filter((x) => x.id !== whId);
        state.focusTasks.unshift({
          id: makeId("cf"), text: wTask.cleanText || wTask.text, why: "", status: "doing",
          isCore: !!wTask.isCore, notePath: null, subLabel: "", subTasks: wTask.subTasks || [], remindAt: wTask.remindAt || ""
        });
        persistQuickTasksMd(); renderAll();
        showToast("Đã đưa lên Hôm nay");
      }
    }
  });

  window.__onReminderPicked = (pickedStr) => {
    if (typeof activeReminderPickerCb === "function") {
      activeReminderPickerCb(pickedStr || "");
    }
  };

  window.__onLinkPreviewLoaded = (url, dataJsonStr) => {
    if (!url) return;
    pendingPreviewUrls.delete(url);
    try {
      const data = typeof dataJsonStr === "string" ? JSON.parse(dataJsonStr) : dataJsonStr;
      if (!data || typeof data !== "object") return;
      state.linkPreviews[url] = data;
      persistLocalLinkPreviews();
      if (data.title) maybeUpgradeRawUrlsInVault(url, data.title);
      renderQuickTab();
      renderNotesTab();
    } catch (_) {}
  };
  window.__onVoiceState = (listening) => els.btnVoice.classList.toggle("listening", !!listening);
  window.__onVoiceText = (txt) => {
    if (!txt) return;
    els.composerInput.value = (els.composerInput.value ? els.composerInput.value + " " : "") + txt;
    syncComposerButtons();
    els.composerInput.focus();
  };
  window.__reloadVaultFromNative = () => { loadVaultState(); showToast("Đã kết nối Vault Syncthing"); };
  window.__openLaunchTab = (tab, focusInput) => {
    if (["quick", "notes", "task"].includes(tab)) {
      state.activeTab = tab; renderAll(); scrollActiveListToBottom();
      if (focusInput) setTimeout(() => els.composerInput.focus(), 120);
    }
  };
  window.__onAppResumed = () => {
    if (!hasNative) return;
    const latest = window.AndroidBridge.getVaultModifiedTimestamp();
    if (latest !== state.lastKnownModified) loadVaultState();
  };
  window.__handleAndroidBack = () => {
    if (!els.bottomSheet.classList.contains("hidden")) { closeSheet(); return true; }
    if (state.searchOpen) { els.btnSearchClear.click(); return true; }
    if (state.activeTab !== "quick") { switchTab("quick"); return true; }
    return false;
  };

  setInterval(() => {
    if (!hasNative || document.hidden) return;
    try {
      const ts = window.AndroidBridge.getVaultModifiedTimestamp();
      if (ts > 0 && ts !== state.lastKnownModified) loadVaultState();
    } catch (_) {}
  }, 2200);

  loadVaultState();
})();

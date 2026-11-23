(function () {
  "use strict";

  const hasNative = typeof window.AndroidBridge !== "undefined";

  const state = {
    activeTab: "quick", // "quick" | "notes" | "task"
    notesDisplayMode: localStorage.getItem("oq_notes_display") || "chat", // "chat" | "block"
    notesTreeLayout: localStorage.getItem("oq_tree_layout") || "split", // "split" | "accordion"
    treeCollapsed: false,
    searchOpen: false,
    searchQuery: "",
    taskTarget: "focus", // "focus" | "warehouse"
    warehouseOpen: false,
    collapsedTaskIds: {},

    vaultConnected: false,
    vaultLabel: "",
    hasAllFilesAccess: false,
    lastKnownModified: 0,
    appVersion: "1.0.0",

    // Parsed data from 0. Inbox/⚡ Quick Notes.md
    quickItems: [], // [{ id, time, text, children: [] }]
    noteStreams: [], // [{ id, group: "active"|"archive", title, items: [{ id, time, text, children: [] }] }]
    activeStreamId: localStorage.getItem("oq_active_stream") || "",

    // Parsed data from daily-focus-anchor.json & ✅ Nhà kho chứa Task.md
    focusAnchorRaw: {},
    focusTasks: [], // [{ id, text, status, isCore, subTasks: [{ id, text, done }] }]
    warehouseRawLines: [],
    warehouseTasks: [], // [{ lineIndex, group, text, done }]
    vaultTags: ["#ui-ux", "#macbook", "#học-tập", "#mua-sắm", "#ý-tưởng"]
  };

  const els = {
    hdrTitle: document.getElementById("hdrTitle"),
    hdrBadge: document.getElementById("hdrBadge"),
    notesHeaderControls: document.getElementById("notesHeaderControls"),
    btnTreeLayout: document.getElementById("btnTreeLayout"),
    btnDisplayMode: document.getElementById("btnDisplayMode"),
    btnAddTop: document.getElementById("btnAddTop"),
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
    notesSplitWrap: document.getElementById("notesSplitWrap"),
    pinnedTreeBox: document.getElementById("pinnedTreeBox"),
    treeFullContent: document.getElementById("treeFullContent"),
    treeCollapsedBar: document.getElementById("treeCollapsedBar"),
    treeCollapsedLabel: document.getElementById("treeCollapsedLabel"),
    activeStreamName: document.getElementById("activeStreamName"),
    btnCollapseTreeManual: document.getElementById("btnCollapseTreeManual"),
    notesStreamList: document.getElementById("notesStreamList"),
    notesAccordionWrap: document.getElementById("notesAccordionWrap"),

    focusTaskList: document.getElementById("focusTaskList"),
    btnToggleWarehouse: document.getElementById("btnToggleWarehouse"),
    warehouseHdrLabel: document.getElementById("warehouseHdrLabel"),
    warehouseChevron: document.getElementById("warehouseChevron"),
    warehouseTaskList: document.getElementById("warehouseTaskList"),

    tagSuggestBar: document.getElementById("tagSuggestBar"),
    composerWrap: document.getElementById("composerWrap"),
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

  function formatRichText(raw) {
    const safe = escHtml(raw);
    return safe.replace(/(#[\p{L}\p{N}_/-]+)/gu, '<span class="tag-pill">$1</span>');
  }

  // =========================================================================
  // PARSER & SERIALIZER: 0. Inbox/⚡ Quick Notes.md (1 SINGLE FILE, TAB INDENT)
  // =========================================================================
  function parseBulletLine(trimmedAfterDash) {
    const m = trimmedAfterDash.match(/^(\d{1,2}:\d{2})\s*[·•-]\s*(.+)$/);
    if (m) {
      return { time: m[1], text: m[2].trim() };
    }
    return { time: "", text: trimmedAfterDash.trim() };
  }

  function parseQuickNotesMd(md) {
    const quickItems = [];
    const streams = [];
    let section = ""; // "quick" | "active" | "archive"
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
        if (/ghi nhanh|nháp|quick/i.test(h1)) {
          section = "quick";
        } else if (/đang giải quyết|vấn đề/i.test(h1)) {
          section = "active";
        } else if (/kho|lưu trữ|cất tạm/i.test(h1)) {
          section = "archive";
        } else {
          section = "active";
        }
        continue;
      }

      if (trimmed.startsWith("## ")) {
        const title = trimmed.slice(3).trim();
        if (!title) continue;
        const grp = section === "archive" ? "archive" : "active";
        currentStream = {
          id: "st_" + grp + "_" + title,
          group: grp,
          title: title,
          items: []
        };
        streams.push(currentStream);
        lastParentItem = null;
        continue;
      }

      // Indented child bullet (Tab \t- or 2+ spaces)
      if (/^(\t+| {2,})-\s+/.test(rawLine)) {
        const childText = rawLine.replace(/^(\t+| {2,})-\s+/, "").trim();
        if (childText && lastParentItem) {
          lastParentItem.children.push(childText);
        }
        continue;
      }

      if (trimmed.startsWith("- ")) {
        const parsed = parseBulletLine(trimmed.slice(2));
        if (!parsed.text) continue;
        const item = {
          id: makeId("ni"),
          time: parsed.time,
          text: parsed.text,
          children: []
        };
        if (section === "quick" || (!currentStream && section !== "active" && section !== "archive")) {
          quickItems.push(item);
          lastParentItem = item;
        } else {
          if (!currentStream) {
            const defaultTitle = section === "archive" ? "Kho Notes chung" : "Bảng cảm giác phấn đấu";
            currentStream = {
              id: "st_" + section + "_" + defaultTitle,
              group: section === "archive" ? "archive" : "active",
              title: defaultTitle,
              items: []
            };
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

  // Strictly follows Vault rules: NO `---` dividers, NO blank line right under Heading, `\t- ` for sub-bullets
  function serializeQuickNotesMd() {
    const out = [];
    out.push("# ⚡ Ghi nhanh");
    for (const item of state.quickItems) {
      const prefix = item.time ? item.time + " · " : "";
      out.push("- " + prefix + item.text);
      for (const ch of item.children || []) {
        out.push("\t- " + ch);
      }
    }

    const activeStreams = state.noteStreams.filter((s) => s.group === "active");
    out.push("# 🎯 Đang giải quyết");
    for (const st of activeStreams) {
      out.push("## " + st.title);
      for (const item of st.items) {
        const prefix = item.time ? item.time + " · " : "";
        out.push("- " + prefix + item.text);
        for (const ch of item.children || []) {
          out.push("\t- " + ch);
        }
      }
    }

    const archiveStreams = state.noteStreams.filter((s) => s.group === "archive");
    out.push("# 📥 Kho lưu trữ chung");
    for (const st of archiveStreams) {
      out.push("## " + st.title);
      for (const item of st.items) {
        const prefix = item.time ? item.time + " · " : "";
        out.push("- " + prefix + item.text);
        for (const ch of item.children || []) {
          out.push("\t- " + ch);
        }
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
  // PARSER & SERIALIZER: TASK (daily-focus-anchor.json + ✅ Nhà kho chứa Task.md)
  // =========================================================================
  function parseDailyAnchor(jsonStr) {
    try {
      const obj = JSON.parse(jsonStr || "{}");
      state.focusAnchorRaw = obj;
      const arr = Array.isArray(obj.customTasks) ? obj.customTasks : [];
      state.focusTasks = arr.map((t) => ({
        ...t,
        id: t.id || makeId("cf"),
        text: t.text || "",
        status: t.status === "done" ? "done" : "doing",
        subTasks: Array.isArray(t.subTasks)
          ? t.subTasks.map((st) => ({
              id: st.id || makeId("st"),
              text: st.text || "",
              done: !!st.done
            }))
          : []
      }));
    } catch (_) {
      state.focusAnchorRaw = { customTasks: [] };
      state.focusTasks = [];
    }
  }

  function persistDailyAnchor() {
    const payload = {
      ...state.focusAnchorRaw,
      customTasks: state.focusTasks
    };
    const jsonStr = JSON.stringify(payload, null, 2);
    if (hasNative) {
      window.AndroidBridge.saveDailyAnchorJson(jsonStr);
      state.lastKnownModified = window.AndroidBridge.getVaultModifiedTimestamp();
    } else {
      localStorage.setItem("oq_web_fallback_anchor", jsonStr);
    }
  }

  function parseWarehouseMd(md) {
    const lines = String(md || "").replace(/\r\n/g, "\n").split("\n");
    state.warehouseRawLines = lines;
    const tasks = [];
    let currentGroup = "Hộp tiếp nhận";
    for (let i = 0; i < lines.length; i++) {
      const tr = lines[i].trim();
      if (tr.startsWith("## ") || tr.startsWith("### ")) {
        currentGroup = tr.replace(/^#+\s*/, "").trim();
        continue;
      }
      const m = tr.match(/^-\s*\[([ xX])\]\s+(.+)$/);
      if (m) {
        tasks.push({
          lineIndex: i,
          group: currentGroup,
          done: m[1].toLowerCase() === "x",
          text: m[2].trim()
        });
      }
    }
    state.warehouseTasks = tasks;
  }

  function persistWarehouseMd() {
    const md = state.warehouseRawLines.join("\n").replace(/\n+$/, "") + "\n";
    if (hasNative) {
      window.AndroidBridge.saveTaskWarehouseMd(md);
      state.lastKnownModified = window.AndroidBridge.getVaultModifiedTimestamp();
    } else {
      localStorage.setItem("oq_web_fallback_wh", md);
    }
  }

  // =========================================================================
  // LOAD STATE FROM NATIVE VAULT BRIDGE
  // =========================================================================
  function loadVaultState() {
    if (hasNative) {
      try {
        const raw = JSON.parse(window.AndroidBridge.getFullVaultState());
        state.vaultConnected = !!raw.connected;
        state.vaultLabel = raw.vaultLabel || "";
        state.hasAllFilesAccess = !!raw.hasAllFilesAccess;
        state.lastKnownModified = raw.lastModified || 0;
        state.appVersion = raw.appVersion || "1.0.0";
        parseQuickNotesMd(raw.quickNotesMd);
        parseDailyAnchor(raw.dailyAnchorJson);
        parseWarehouseMd(raw.taskWarehouseMd);
        try {
          const parsedTags = JSON.parse(raw.tagsJson || "[]");
          if (Array.isArray(parsedTags) && parsedTags.length > 0) {
            state.vaultTags = parsedTags.slice(0, 30).map((t) => {
              const s = typeof t === "string" ? t : t.tag || "";
              return s.startsWith("#") ? s : "#" + s;
            }).filter((t) => t.length > 1);
          }
        } catch (_) {}
      } catch (_) {}
    } else {
      state.vaultConnected = true;
      state.vaultLabel = "Obsidian (Bản chính)";
      parseQuickNotesMd(localStorage.getItem("oq_web_fallback_md") || "");
      parseDailyAnchor(localStorage.getItem("oq_web_fallback_anchor") || '{"customTasks":[]}');
      parseWarehouseMd(localStorage.getItem("oq_web_fallback_wh") || "## 📥 1. Hộp tiếp nhận\n");
    }
    renderAll();
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
    if (Array.isArray(children)) {
      return children.some((c) => String(c || "").toLowerCase().includes(q));
    }
    return false;
  }

  function renderHeaderAndNav() {
    // Vault banner
    if (!state.vaultConnected) {
      els.vaultBanner.classList.remove("hidden");
      els.vaultBannerText.textContent = state.hasAllFilesAccess
        ? "Chọn thư mục Vault Syncthing để đồng bộ 2 chiều"
        : "Bật quyền tệp để tự nhận diện Vault Syncthing";
    } else {
      els.vaultBanner.classList.add("hidden");
    }

    // Bottom tabs
    document.querySelectorAll(".nav-tab").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.tab === state.activeTab);
    });
    els.viewQuick.classList.toggle("active", state.activeTab === "quick");
    els.viewNotes.classList.toggle("active", state.activeTab === "notes");
    els.viewTask.classList.toggle("active", state.activeTab === "task");

    // Badges
    const qCount = state.quickItems.length;
    els.badgeQuick.textContent = qCount;
    els.badgeQuick.classList.toggle("hidden", qCount === 0);

    const doingCount = state.focusTasks.filter((t) => t.status !== "done").length;
    els.badgeTask.textContent = doingCount;
    els.badgeTask.classList.toggle("hidden", doingCount === 0);

    // Header per tab
    if (state.activeTab === "quick") {
      els.hdrTitle.textContent = "Ghi nhanh";
      els.hdrBadge.textContent = qCount + " chờ dọn";
      els.hdrBadge.classList.toggle("hidden", qCount === 0);
      els.notesHeaderControls.classList.add("hidden");
      els.btnAddTop.classList.add("hidden");
      els.btnTaskTargetToggle.classList.add("hidden");
      els.composerInput.placeholder = "Gõ luôn vào đây, nghĩ chỗ cất sau...";
    } else if (state.activeTab === "notes") {
      els.hdrTitle.textContent = "Ghi chú";
      els.hdrBadge.classList.add("hidden");
      els.notesHeaderControls.classList.remove("hidden");
      els.btnAddTop.classList.remove("hidden");
      els.btnTaskTargetToggle.classList.add("hidden");
      els.btnTreeLayout.textContent = state.notesTreeLayout === "split" ? "⬒ Ghim" : "▾ Gập mở";
      els.btnDisplayMode.querySelectorAll(".seg-opt").forEach((sp) => {
        sp.classList.toggle("active", sp.dataset.mode === state.notesDisplayMode);
      });
      const st = getActiveStream();
      els.composerInput.placeholder = st ? "Ghi vào " + st.title + "..." : "Ghi vào mạch này...";
    } else {
      els.hdrTitle.textContent = "Trọng tâm hôm nay";
      els.hdrBadge.classList.add("hidden");
      els.notesHeaderControls.classList.add("hidden");
      els.btnAddTop.classList.remove("hidden");
      els.btnTaskTargetToggle.classList.remove("hidden");
      els.btnTaskTargetToggle.textContent = state.taskTarget === "focus" ? "🔥 Hôm nay" : "📋 Kho chờ";
      els.composerInput.placeholder = state.taskTarget === "focus"
        ? "Thêm việc trọng tâm hôm nay..."
        : "Thêm việc vào Kho chờ Task...";
    }
  }

  function renderQuickTab() {
    const items = state.quickItems.filter((it) => matchesQuery(it.text, it.children));
    if (items.length === 0) {
      els.quickList.innerHTML = '<div class="empty-box">Khay nháp sạch bong.<br/>Mọi ý chợt nảy ra cứ gõ thẳng xuống dưới, rảnh tay bấm ⋯ dọn sau.</div>';
      return;
    }

    els.quickList.innerHTML = items.map((it) => {
      const childrenHtml = (it.children && it.children.length)
        ? '<div class="ne-children">' + it.children.map((c) => '<div class="ne-child">' + formatRichText(c) + "</div>").join("") + "</div>"
        : "";
      return (
        '<div class="quick-item" data-id="' + it.id + '">' +
          '<div class="qi-body" data-edit-quick="' + it.id + '">' +
            (it.time ? '<div class="qi-time">' + escHtml(it.time) + "</div>" : "") +
            '<div class="qi-text">' + formatRichText(it.text) + "</div>" +
            childrenHtml +
          "</div>" +
          '<button class="dots-btn" data-triage="' + it.id + '" type="button">⋯</button>' +
        "</div>"
      );
    }).join("");
  }

  function buildStreamEntriesHtml(stream) {
    if (!stream) return "";
    const filtered = stream.items.filter((it) => matchesQuery(it.text, it.children));
    if (filtered.length === 0) {
      return '<div class="empty-box">Chưa có ghi chú trong mạch này.</div>';
    }
    return filtered.map((it) => {
      const subHtml = (it.children && it.children.length)
        ? '<div class="ne-children">' + it.children.map((c) => '<div class="ne-child">' + formatRichText(c) + "</div>").join("") + "</div>"
        : "";
      return (
        '<div class="note-entry" data-entry-id="' + it.id + '" data-stream-id="' + stream.id + '">' +
          (it.time ? '<div class="ne-meta"><span>' + escHtml(it.time) + "</span></div>" : "") +
          '<div class="ne-text">' + formatRichText(it.text) + "</div>" +
          subHtml +
        "</div>"
      );
    }).join("");
  }

  function renderNotesTab() {
    const activeStreams = state.noteStreams.filter((s) => s.group === "active");
    const archiveStreams = state.noteStreams.filter((s) => s.group === "archive");
    const curStream = getActiveStream();

    if (state.notesTreeLayout === "split") {
      els.notesSplitWrap.classList.remove("hidden");
      els.notesAccordionWrap.classList.add("hidden");

      // Tree content (Đang giải quyết on TOP, Kho lưu trữ chung BELOW)
      const renderRows = (arr, icon) => arr.map((s) => {
        const isOn = curStream && s.id === curStream.id;
        return (
          '<div class="tree-row ' + (isOn ? "active" : "") + '" data-select-stream="' + escHtml(s.id) + '" data-manage-stream="' + escHtml(s.id) + '">' +
            '<div class="tr-left"><span>' + (isOn ? "●" : icon) + '</span><span class="tr-title">' + escHtml(s.title) + "</span></div>" +
            '<span class="tr-count">' + s.items.length + "</span>" +
          "</div>"
        );
      }).join("");

      els.treeFullContent.innerHTML =
        '<div class="tree-group-hdr"><span>Đang giải quyết</span><button class="tree-add-mini" data-add-stream="active" type="button">＋</button></div>' +
        renderRows(activeStreams, "○") +
        '<div class="tree-group-hdr tree-group-sep"><span>Kho lưu trữ chung</span><button class="tree-add-mini" data-add-stream="archive" type="button">＋</button></div>' +
        renderRows(archiveStreams, "📥");

      if (curStream) {
        const grpLabel = curStream.group === "active" ? "🎯 Đang giải quyết" : "📥 Kho lưu trữ";
        els.treeCollapsedLabel.textContent = grpLabel + " · " + curStream.title;
        els.activeStreamName.textContent = curStream.title;
      }

      els.treeFullContent.classList.toggle("hidden", state.treeCollapsed);
      els.treeCollapsedBar.classList.toggle("hidden", !state.treeCollapsed);
      els.btnCollapseTreeManual.classList.toggle("hidden", state.treeCollapsed);

      els.notesStreamList.classList.toggle("chat-mode", state.notesDisplayMode === "chat");
      els.notesStreamList.innerHTML = buildStreamEntriesHtml(curStream);
    } else {
      // Inline Accordion Mode (Cơ chế 2)
      els.notesSplitWrap.classList.add("hidden");
      els.notesAccordionWrap.classList.remove("hidden");

      const buildAccGroup = (arr) => arr.map((s) => {
        const isOpen = curStream && s.id === curStream.id;
        const bodyClass = "acc-body " + (state.notesDisplayMode === "chat" ? "stream-list chat-mode" : "");
        return (
          '<div class="acc-card ' + (isOpen ? "open" : "") + '">' +
            '<div class="acc-hdr" data-select-stream="' + escHtml(s.id) + '">' +
              '<span>' + (isOpen ? "▾ " : "▸ ") + escHtml(s.title) + "</span>" +
              '<span class="tr-count">' + s.items.length + "</span>" +
            "</div>" +
            (isOpen ? '<div class="' + bodyClass + '">' + buildStreamEntriesHtml(s) + "</div>" : "") +
          "</div>"
        );
      }).join("");

      els.notesAccordionWrap.innerHTML =
        '<div class="acc-section-label"><span>Đang giải quyết</span><button class="tree-add-mini" data-add-stream="active" type="button">＋ Thêm mạch</button></div>' +
        buildAccGroup(activeStreams) +
        '<div class="acc-section-label" style="margin-top:14px;"><span>Kho lưu trữ chung</span><button class="tree-add-mini" data-add-stream="archive" type="button">＋ Thêm kho</button></div>' +
        buildAccGroup(archiveStreams);
    }
  }

  function renderTaskTab() {
    const focusFiltered = state.focusTasks.filter((t) =>
      matchesQuery(t.text, (t.subTasks || []).map((s) => s.text))
    );

    if (focusFiltered.length === 0) {
      els.focusTaskList.innerHTML = '<div class="empty-box">Chưa có việc trọng tâm hôm nay.<br/>Gõ bên dưới hoặc đẩy từ Nháp / Kho chờ lên.</div>';
    } else {
      els.focusTaskList.innerHTML = focusFiltered.map((t) => {
        const hasSubs = Array.isArray(t.subTasks) && t.subTasks.length > 0;
        const isDone = t.status === "done";
        const isCollapsed = !!state.collapsedTaskIds[t.id];

        const leftBtnClass = hasSubs && !isDone
          ? "task-node-btn parent-plus"
          : "task-node-btn" + (isDone ? " checked" : "");
        const leftSymbol = isDone ? "✓" : (hasSubs ? "+" : "");

        const subTreeHtml = (hasSubs && !isCollapsed)
          ? '<div class="subtask-tree">' +
              t.subTasks.map((st) => (
                '<div class="subtask-row">' +
                  '<button class="task-node-btn ' + (st.done ? "checked" : "") + '" data-toggle-sub="' + escHtml(t.id) + "|" + escHtml(st.id) + '" type="button">' +
                    (st.done ? "✓" : "") +
                  "</button>" +
                  '<span class="st-label ' + (st.done ? "done" : "") + '" data-edit-sub="' + escHtml(t.id) + "|" + escHtml(st.id) + '">' +
                    escHtml(st.text) +
                  "</span>" +
                "</div>"
              )).join("") +
            "</div>"
          : "";

        return (
          '<div class="task-card" data-task-id="' + escHtml(t.id) + '">' +
            '<div class="tc-top-row">' +
              '<button class="' + leftBtnClass + '" data-task-circle="' + escHtml(t.id) + '" type="button">' + leftSymbol + "</button>" +
              '<span class="tc-label ' + (isDone ? "done" : "") + '" data-task-menu="' + escHtml(t.id) + '">' + escHtml(t.text) + "</span>" +
              (hasSubs
                ? '<button class="tc-chevron" data-collapse-task="' + escHtml(t.id) + '" type="button">' + (isCollapsed ? "⌄" : "⌃") + "</button>"
                : '<button class="tc-chevron" data-add-subtask="' + escHtml(t.id) + '" type="button">＋</button>') +
            "</div>" +
            subTreeHtml +
          "</div>"
        );
      }).join("");
    }

    // Warehouse tasks
    const pendingWh = state.warehouseTasks.filter((w) => !w.done && matchesQuery(w.text));
    els.warehouseHdrLabel.textContent = "📥 Kho chờ Task (" + pendingWh.length + ")";
    els.warehouseChevron.textContent = state.warehouseOpen ? "⌃" : "⌄";
    els.warehouseTaskList.classList.toggle("hidden", !state.warehouseOpen);

    if (state.warehouseOpen) {
      if (pendingWh.length === 0) {
        els.warehouseTaskList.innerHTML = '<div class="empty-box" style="padding:18px;">Kho chờ trống.</div>';
      } else {
        let lastGrp = "";
        const html = [];
        for (const w of pendingWh) {
          if (w.group !== lastGrp) {
            lastGrp = w.group;
            html.push('<div class="wh-group-title">' + escHtml(w.group) + "</div>");
          }
          html.push(
            '<div class="task-card" style="padding:11px 12px;margin-bottom:8px;">' +
              '<div class="tc-top-row">' +
                '<button class="task-node-btn" data-check-wh="' + w.lineIndex + '" type="button"></button>' +
                '<span class="tc-label" style="font-size:14.5px;">' + escHtml(w.text) + "</span>" +
                '<button class="promote-btn" data-promote-wh="' + w.lineIndex + '" type="button">🔥 Hôm nay</button>' +
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
  // BOTTOM SHEET MODALS (Triage ⋯, Edit, Add Stream, Settings)
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

  // ⋯ Triage Sheet from ⚡ Nháp -> Vanish & Move!
  function openQuickTriageSheet(itemId) {
    const item = state.quickItems.find((x) => x.id === itemId);
    if (!item) return;
    vibrate(10);

    const activeStreams = state.noteStreams.filter((s) => s.group === "active");
    const archiveStreams = state.noteStreams.filter((s) => s.group === "archive");

    let html = '<div class="sheet-title">Đẩy mục này sang đâu?</div>';
    html += '<div class="sheet-group-label">Chuyển vào Đang giải quyết</div>';
    for (const st of activeStreams) {
      html += '<button class="sheet-row" data-move-to-stream="' + escHtml(st.id) + '" type="button"><span>🎯 ' + escHtml(st.title) + '</span><span>➔</span></button>';
    }
    html += '<div class="sheet-group-label">Chuyển vào Kho lưu trữ chung</div>';
    for (const st of archiveStreams) {
      html += '<button class="sheet-row" data-move-to-stream="' + escHtml(st.id) + '" type="button"><span>📥 ' + escHtml(st.title) + '</span><span>➔</span></button>';
    }
    html += '<div class="sheet-group-label">Biến thành Công việc (Task)</div>';
    html += '<button class="sheet-row" data-move-to-task="focus" type="button"><span>🔥 Trọng tâm hôm nay</span><span>➔</span></button>';
    html += '<button class="sheet-row" data-move-to-task="warehouse" type="button"><span>📋 Kho chờ Task</span><span>➔</span></button>';

    html += '<div class="sheet-inline-actions">' +
      '<button class="sheet-row" data-sheet-edit-quick="' + escHtml(item.id) + '" type="button">✎ Sửa</button>' +
      '<button class="sheet-row danger" data-sheet-del-quick="' + escHtml(item.id) + '" type="button">🗑 Xóa</button>' +
    "</div>";

    openSheet(html);

    els.sheetContent.querySelectorAll("[data-move-to-stream]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const targetId = btn.getAttribute("data-move-to-stream");
        const targetStream = state.noteStreams.find((s) => s.id === targetId);
        if (!targetStream) return;
        closeSheet();
        vanishQuickItemAndRun(item.id, () => {
          targetStream.items.push({
            id: makeId("ni"),
            time: item.time || nowHHMM(),
            text: item.text,
            children: item.children || []
          });
          persistQuickNotes();
          showToast("✓ Đã dọn sang " + targetStream.title);
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
            showToast("✓ Đã chuyển vào Trọng tâm hôm nay");
          } else {
            insertIntoWarehouse(item.text);
            showToast("✓ Đã chuyển vào Kho chờ Task");
          }
        });
      });
    });

    const editBtn = els.sheetContent.querySelector("[data-sheet-edit-quick]");
    if (editBtn) {
      editBtn.addEventListener("click", () => openEditNoteSheet("quick", null, item));
    }
    const delBtn = els.sheetContent.querySelector("[data-sheet-del-quick]");
    if (delBtn) {
      delBtn.addEventListener("click", () => {
        closeSheet();
        vanishQuickItemAndRun(item.id, () => {
          persistQuickNotes();
          showToast("Đã xóa mục nháp");
        });
      });
    }
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
    const idx = state.warehouseRawLines.findIndex((l) => /Hộp tiếp nhận/i.test(l));
    if (idx >= 0) {
      let ins = idx + 1;
      while (ins < state.warehouseRawLines.length && state.warehouseRawLines[ins].trim().startsWith("*(")) {
        ins++;
      }
      state.warehouseRawLines.splice(ins, 0, "- [ ] " + taskText);
    } else {
      state.warehouseRawLines.push("- [ ] " + taskText);
    }
    parseWarehouseMd(state.warehouseRawLines.join("\n"));
    persistWarehouseMd();
  }

  function openEditNoteSheet(scope, stream, item) {
    let html = '<div class="sheet-title">Chỉnh sửa nội dung</div>';
    html += '<textarea id="modalEditArea" class="modal-input" rows="3">' + escHtml(item.text) + "</textarea>";

    if (item.children && item.children.length > 0) {
      html += '<div class="sheet-group-label">Các ý con đã gộp (Chạm ↗ để tách riêng)</div>';
      item.children.forEach((ch, idx) => {
        html += '<button class="sheet-row" data-unmerge-idx="' + idx + '" type="button"><span>' + escHtml(ch) + '</span><span style="color:#38bdf8;">↗ Tách</span></button>';
      });
    }

    html += '<button id="btnSaveNoteEdit" class="primary-btn" type="button">Lưu thay đổi</button>';
    html += '<div class="sheet-inline-actions">' +
      '<button id="btnCopyNoteText" class="sheet-row" type="button">📋 Sao chép</button>' +
      '<button id="btnDeleteNoteEntry" class="sheet-row danger" type="button">🗑 Xóa mục</button>' +
    "</div>";

    openSheet(html);
    const area = document.getElementById("modalEditArea");

    document.getElementById("btnSaveNoteEdit").addEventListener("click", () => {
      const val = area.value.trim();
      if (val) {
        item.text = val;
        persistQuickNotes();
        renderAll();
      }
      closeSheet();
    });

    document.getElementById("btnCopyNoteText").addEventListener("click", () => {
      const full = [item.text].concat(item.children || []).join("\n- ");
      if (hasNative) window.AndroidBridge.setClipboardText(full);
      closeSheet();
      showToast("Đã sao chép");
    });

    document.getElementById("btnDeleteNoteEntry").addEventListener("click", () => {
      if (scope === "quick") {
        state.quickItems = state.quickItems.filter((x) => x.id !== item.id);
      } else if (stream) {
        stream.items = stream.items.filter((x) => x.id !== item.id);
      }
      persistQuickNotes();
      closeSheet();
      renderAll();
      showToast("Đã xóa");
    });

    els.sheetContent.querySelectorAll("[data-unmerge-idx]").forEach((btn) => {
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
  }

  function openAddStreamSheet(defaultGroup) {
    const grp = defaultGroup === "archive" ? "archive" : "active";
    const label = grp === "active" ? "Thêm vấn đề Đang giải quyết" : "Thêm ngăn Kho lưu trữ";
    openSheet(
      '<div class="sheet-title">' + label + "</div>" +
      '<input id="newStreamTitleInput" class="modal-input" type="text" placeholder="Nhập tên mạch (tự tạo Heading ## trong file)..." />' +
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
      showToast("✓ Đã tạo ## " + name);
    });
  }

  function openTaskActionSheet(task) {
    let html = '<div class="sheet-title">Tùy chỉnh công việc</div>';
    html += '<input id="editTaskTitleInp" class="modal-input" type="text" value="' + escHtml(task.text) + '" />';
    html += '<button id="btnSaveTaskTitle" class="primary-btn" style="margin-bottom:10px;" type="button">Lưu tên việc</button>';
    html += '<button id="btnSheetAddSub" class="sheet-row" type="button"><span>＋ Thêm việc con vào nhánh cây</span><span>➔</span></button>';
    html += '<button id="btnSheetToggleDone" class="sheet-row" type="button"><span>' +
      (task.status === "done" ? "↺ Đánh dấu chưa xong" : "✓ Hoàn thành công việc này") +
      "</span></button>";
    html += '<button id="btnSheetMoveToWh" class="sheet-row" type="button"><span>📥 Hạ xuống Kho chờ Task</span><span>➔</span></button>';
    html += '<button id="btnSheetDeleteTask" class="sheet-row danger" type="button"><span>🗑 Xóa công việc</span></button>';

    openSheet(html);

    document.getElementById("btnSaveTaskTitle").addEventListener("click", () => {
      const v = document.getElementById("editTaskTitleInp").value.trim();
      if (v) {
        task.text = v;
        persistDailyAnchor();
        renderAll();
      }
      closeSheet();
    });

    document.getElementById("btnSheetAddSub").addEventListener("click", () => {
      openAddSubtaskSheet(task);
    });

    document.getElementById("btnSheetToggleDone").addEventListener("click", () => {
      task.status = task.status === "done" ? "doing" : "done";
      persistDailyAnchor();
      closeSheet();
      renderAll();
    });

    document.getElementById("btnSheetMoveToWh").addEventListener("click", () => {
      state.focusTasks = state.focusTasks.filter((x) => x.id !== task.id);
      persistDailyAnchor();
      insertIntoWarehouse(task.text);
      closeSheet();
      renderAll();
      showToast("Đã chuyển xuống Kho chờ");
    });

    document.getElementById("btnSheetDeleteTask").addEventListener("click", () => {
      state.focusTasks = state.focusTasks.filter((x) => x.id !== task.id);
      persistDailyAnchor();
      closeSheet();
      renderAll();
    });
  }

  function openAddSubtaskSheet(parentTask) {
    openSheet(
      '<div class="sheet-title">Thêm việc con cho "' + escHtml(parentTask.text) + '"</div>' +
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
      persistDailyAnchor();
      closeSheet();
      renderAll();
    });
  }

  function openSettingsSheet() {
    const statusText = state.vaultConnected
      ? "✓ Đã kết nối: " + state.vaultLabel
      : "Chưa chọn thư mục Vault";
    openSheet(
      '<div class="sheet-title">Đồng bộ & Cập nhật (v' + escHtml(state.appVersion) + ")</div>" +
      '<div class="sheet-group-label">Thư mục Obsidian Vault (Syncthing)</div>' +
      '<button id="btnPickVaultSheet" class="sheet-row" type="button"><span>📁 ' + escHtml(statusText) + '</span><span style="color:#38bdf8;">Chọn lại</span></button>' +
      (!state.hasAllFilesAccess
        ? '<button id="btnGrantAllFilesSheet" class="sheet-row" type="button"><span>⚡ Cấp quyền đọc/ghi trực tiếp tốc độ cao</span><span style="color:#38bdf8;">Bật</span></button>'
        : "") +
      '<div class="sheet-group-label">Cập nhật tự động từ GitHub (OTA)</div>' +
      '<button id="btnCheckOtaSheet" class="sheet-row" type="button"><span>🔄 Kiểm tra bản cập nhật mới ngay</span><span>➔</span></button>' +
      '<button id="btnReloadVaultSheet" class="sheet-row" type="button"><span>↻ Đọc lại dữ liệu từ Syncthing</span><span>➔</span></button>'
    );

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
  // COMPOSER SUBMISSION & TAG AUTOCOMPLETE
  // =========================================================================
  function submitComposer() {
    const raw = els.composerInput.value.trim();
    if (!raw) return;
    vibrate(10);

    if (state.activeTab === "quick") {
      state.quickItems.unshift({
        id: makeId("ni"),
        time: nowHHMM(),
        text: raw,
        children: []
      });
      persistQuickNotes();
      els.quickList.scrollTop = 0;
    } else if (state.activeTab === "notes") {
      const st = getActiveStream();
      if (!st) return;
      st.items.push({
        id: makeId("ni"),
        time: nowHHMM(),
        text: raw,
        children: []
      });
      persistQuickNotes();
      setTimeout(() => {
        els.notesStreamList.scrollTop = els.notesStreamList.scrollHeight;
      }, 20);
    } else if (state.activeTab === "task") {
      if (state.taskTarget === "focus") {
        state.focusTasks.unshift({
          id: makeId("cf"),
          text: raw,
          why: "",
          status: "doing",
          isCore: false,
          notePath: null,
          subLabel: "",
          subTasks: []
        });
        persistDailyAnchor();
      } else {
        insertIntoWarehouse(raw);
      }
    }

    els.composerInput.value = "";
    els.composerInput.style.height = "auto";
    els.tagSuggestBar.classList.add("hidden");
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
    els.composerInput.focus();
  });

  // Hide bottom bar when soft keyboard opens so input sits flush above keyboard
  function updateKeyboardOpenState() {
    const activeEl = document.activeElement;
    const inputFocused = activeEl === els.composerInput || activeEl === els.searchInput;
    const shrunk = window.visualViewport ? window.visualViewport.height < window.screen.height * 0.76 : false;
    const isOpen = inputFocused && shrunk;
    document.body.classList.toggle("keyboard-open", isOpen || inputFocused);
    if ((isOpen || inputFocused) && state.activeTab === "notes" && state.notesTreeLayout === "split") {
      state.treeCollapsed = true;
      els.treeFullContent.classList.add("hidden");
      els.treeCollapsedBar.classList.remove("hidden");
      els.btnCollapseTreeManual.classList.add("hidden");
    }
  }

  els.composerInput.addEventListener("focus", updateKeyboardOpenState);
  els.composerInput.addEventListener("blur", () => {
    setTimeout(() => document.body.classList.remove("keyboard-open"), 120);
  });
  if (window.visualViewport) {
    window.visualViewport.addEventListener("resize", updateKeyboardOpenState);
  }

  // Auto-collapse pinned tree when scrolling down notes stream
  els.notesStreamList.addEventListener("scroll", () => {
    if (els.notesStreamList.scrollTop > 36 && !state.treeCollapsed) {
      state.treeCollapsed = true;
      els.treeFullContent.classList.add("hidden");
      els.treeCollapsedBar.classList.remove("hidden");
      els.btnCollapseTreeManual.classList.add("hidden");
    }
  });

  els.treeCollapsedBar.addEventListener("click", () => {
    state.treeCollapsed = false;
    renderNotesTab();
  });

  els.btnCollapseTreeManual.addEventListener("click", () => {
    state.treeCollapsed = true;
    renderNotesTab();
  });

  // =========================================================================
  // LONG-PRESS BODY DRAG: REORDER OR MERGE BUBBLES / CARDS (ZERO `::` GRIPS!)
  // =========================================================================
  let dragState = null;

  function setupLongPressMergeDrag(container) {
    let pressTimer = null;
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
        const stream = state.noteStreams.find((s) => s.id === streamId);
        const item = stream ? stream.items.find((x) => x.id === entryId) : null;
        if (!stream || !item) return;

        vibrate(18);
        card.classList.add("dragging-source");
        const ghost = document.createElement("div");
        ghost.className = "drag-ghost";
        ghost.textContent = item.text;
        ghost.style.left = Math.max(12, startX - 90) + "px";
        ghost.style.top = (startY - 26) + "px";
        document.body.appendChild(ghost);

        dragState = {
          stream,
          item,
          sourceEl: card,
          ghostEl: ghost,
          targetEl: null,
          dropAction: null // "merge" | "before" | "after"
        };
      }, 230);
    }, { passive: true });

    container.addEventListener("touchmove", (e) => {
      if (!dragState) {
        if (pressTimer) {
          const dx = Math.abs(e.touches[0].clientX - startX);
          const dy = Math.abs(e.touches[0].clientY - startY);
          if (dx > 10 || dy > 10) {
            clearTimeout(pressTimer);
            pressTimer = null;
          }
        }
        return;
      }

      e.preventDefault();
      const touch = e.touches[0];
      dragState.ghostEl.style.left = Math.max(12, Math.min(window.innerWidth - 220, touch.clientX - 90)) + "px";
      dragState.ghostEl.style.top = (touch.clientY - 28) + "px";

      clearDragHighlights();
      dragState.ghostEl.style.display = "none";
      const under = document.elementFromPoint(touch.clientX, touch.clientY);
      dragState.ghostEl.style.display = "";

      const targetCard = under ? under.closest(".note-entry") : null;
      if (!targetCard || targetCard === dragState.sourceEl) {
        dragState.targetEl = null;
        dragState.dropAction = null;
        return;
      }
      if (targetCard.getAttribute("data-stream-id") !== dragState.stream.id) return;

      const rect = targetCard.getBoundingClientRect();
      const relY = (touch.clientY - rect.top) / Math.max(1, rect.height);
      dragState.targetEl = targetCard;

      if (relY < 0.24) {
        dragState.dropAction = "before";
        targetCard.classList.add("reorder-above");
      } else if (relY > 0.76) {
        dragState.dropAction = "after";
        targetCard.classList.add("reorder-below");
      } else {
        dragState.dropAction = "merge";
        targetCard.classList.add("merge-target");
      }
    }, { passive: false });

    const finishTouch = () => {
      if (pressTimer) {
        clearTimeout(pressTimer);
        pressTimer = null;
      }
      if (!dragState) return;

      const { stream, item, targetEl, dropAction, ghostEl, sourceEl } = dragState;
      dragState = null;
      sourceEl.classList.remove("dragging-source");
      ghostEl.remove();
      clearDragHighlights();

      if (targetEl && dropAction) {
        const targetId = targetEl.getAttribute("data-entry-id");
        const targetItem = stream.items.find((x) => x.id === targetId);
        if (targetItem && targetItem.id !== item.id) {
          vibrate(16);
          const srcIdx = stream.items.indexOf(item);
          if (srcIdx >= 0) stream.items.splice(srcIdx, 1);

          if (dropAction === "merge") {
            targetItem.children = targetItem.children || [];
            targetItem.children.push(item.text);
            if (item.children && item.children.length) {
              targetItem.children.push(...item.children);
            }
            showToast("✓ Đã gộp chung vào 1 khối");
          } else {
            const tgtIdx = stream.items.indexOf(targetItem);
            const insertAt = dropAction === "before" ? tgtIdx : tgtIdx + 1;
            stream.items.splice(insertAt, 0, item);
          }
          persistQuickNotes();
          renderNotesTab();
        }
      }
    };

    container.addEventListener("touchend", finishTouch);
    container.addEventListener("touchcancel", finishTouch);
  }

  function clearDragHighlights() {
    document.querySelectorAll(".merge-target, .reorder-above, .reorder-below").forEach((el) => {
      el.classList.remove("merge-target", "reorder-above", "reorder-below");
    });
  }

  setupLongPressMergeDrag(els.notesStreamList);
  setupLongPressMergeDrag(els.notesAccordionWrap);

  // =========================================================================
  // EVENT DELEGATION & SWIPE BETWEEN TABS
  // =========================================================================
  els.bottomBar.addEventListener("click", (e) => {
    const btn = e.target.closest(".nav-tab");
    if (!btn) return;
    switchTab(btn.dataset.tab);
  });

  function switchTab(tab) {
    if (!["quick", "notes", "task"].includes(tab)) return;
    state.activeTab = tab;
    vibrate(8);
    renderAll();
  }

  // Horizontal swipe across tabs
  let swipeX = 0;
  let swipeY = 0;
  els.viewport.addEventListener("touchstart", (e) => {
    if (e.touches.length !== 1 || dragState) return;
    swipeX = e.touches[0].clientX;
    swipeY = e.touches[0].clientY;
  }, { passive: true });

  els.viewport.addEventListener("touchend", (e) => {
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

  // Header controls
  els.btnTreeLayout.addEventListener("click", () => {
    state.notesTreeLayout = state.notesTreeLayout === "split" ? "accordion" : "split";
    localStorage.setItem("oq_tree_layout", state.notesTreeLayout);
    state.treeCollapsed = false;
    vibrate(8);
    renderAll();
  });

  els.btnDisplayMode.addEventListener("click", (e) => {
    const opt = e.target.closest(".seg-opt");
    const next = opt ? opt.dataset.mode : (state.notesDisplayMode === "chat" ? "block" : "chat");
    state.notesDisplayMode = next;
    localStorage.setItem("oq_notes_display", next);
    vibrate(8);
    renderAll();
  });

  els.btnAddTop.addEventListener("click", () => {
    if (state.activeTab === "notes") {
      openAddStreamSheet("active");
    } else if (state.activeTab === "task") {
      els.composerInput.focus();
    }
  });

  els.btnSearchToggle.addEventListener("click", () => {
    state.searchOpen = !state.searchOpen;
    els.searchBarWrap.classList.toggle("hidden", !state.searchOpen);
    if (state.searchOpen) {
      els.searchInput.focus();
    } else {
      state.searchQuery = "";
      els.searchInput.value = "";
      renderAll();
    }
  });

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
    if (!state.hasAllFilesAccess) {
      window.AndroidBridge.requestAllFilesAccess();
    } else {
      window.AndroidBridge.pickVaultFolder();
    }
  });

  els.btnTaskTargetToggle.addEventListener("click", () => {
    state.taskTarget = state.taskTarget === "focus" ? "warehouse" : "focus";
    vibrate(8);
    renderHeaderAndNav();
  });

  els.btnVoice.addEventListener("click", () => {
    if (hasNative && window.AndroidBridge.startVoiceInput) {
      window.AndroidBridge.startVoiceInput("vi-VN");
    }
  });

  // Quick tab clicks
  els.quickList.addEventListener("click", (e) => {
    const triageBtn = e.target.closest("[data-triage]");
    if (triageBtn) {
      openQuickTriageSheet(triageBtn.getAttribute("data-triage"));
      return;
    }
    const editArea = e.target.closest("[data-edit-quick]");
    if (editArea) {
      const item = state.quickItems.find((x) => x.id === editArea.getAttribute("data-edit-quick"));
      if (item) openEditNoteSheet("quick", null, item);
    }
  });

  // Notes tab clicks
  els.viewNotes.addEventListener("click", (e) => {
    if (dragState) return;
    const addStreamBtn = e.target.closest("[data-add-stream]");
    if (addStreamBtn) {
      openAddStreamSheet(addStreamBtn.getAttribute("data-add-stream"));
      return;
    }
    const selRow = e.target.closest("[data-select-stream]");
    if (selRow) {
      const id = selRow.getAttribute("data-select-stream");
      state.activeStreamId = id;
      localStorage.setItem("oq_active_stream", id);
      vibrate(8);
      renderAll();
      return;
    }
    const entryEl = e.target.closest(".note-entry");
    if (entryEl) {
      const st = state.noteStreams.find((s) => s.id === entryEl.getAttribute("data-stream-id"));
      const item = st ? st.items.find((x) => x.id === entryEl.getAttribute("data-entry-id")) : null;
      if (st && item) openEditNoteSheet("notes", st, item);
    }
  });

  // Task tab clicks
  els.viewTask.addEventListener("click", (e) => {
    const circleBtn = e.target.closest("[data-task-circle]");
    if (circleBtn) {
      const t = state.focusTasks.find((x) => x.id === circleBtn.getAttribute("data-task-circle"));
      if (!t) return;
      vibrate(12);
      if (t.subTasks && t.subTasks.length > 0 && t.status !== "done") {
        openAddSubtaskSheet(t);
      } else {
        t.status = t.status === "done" ? "doing" : "done";
        persistDailyAnchor();
        renderAll();
      }
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
      if (st) {
        vibrate(12);
        st.done = !st.done;
        persistDailyAnchor();
        renderAll();
      }
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
          '<button id="btnDelSub" class="sheet-row danger" type="button">🗑 Xóa việc con</button>'
        );
        document.getElementById("btnSaveSub").addEventListener("click", () => {
          const v = document.getElementById("subEditInp").value.trim();
          if (v) st.text = v;
          persistDailyAnchor();
          closeSheet();
          renderAll();
        });
        document.getElementById("btnDelSub").addEventListener("click", () => {
          t.subTasks = t.subTasks.filter((x) => x.id !== sid);
          persistDailyAnchor();
          closeSheet();
          renderAll();
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
    if (whHdr) {
      state.warehouseOpen = !state.warehouseOpen;
      renderTaskTab();
      return;
    }

    const checkWh = e.target.closest("[data-check-wh]");
    if (checkWh) {
      const lnIdx = parseInt(checkWh.getAttribute("data-check-wh"), 10);
      if (state.warehouseRawLines[lnIdx]) {
        vibrate(12);
        state.warehouseRawLines[lnIdx] = state.warehouseRawLines[lnIdx].replace("- [ ]", "- [x]");
        parseWarehouseMd(state.warehouseRawLines.join("\n"));
        persistWarehouseMd();
        renderAll();
      }
      return;
    }

    const promWh = e.target.closest("[data-promote-wh]");
    if (promWh) {
      const lnIdx = parseInt(promWh.getAttribute("data-promote-wh"), 10);
      const wTask = state.warehouseTasks.find((w) => w.lineIndex === lnIdx);
      if (wTask) {
        vibrate(14);
        state.warehouseRawLines.splice(lnIdx, 1);
        parseWarehouseMd(state.warehouseRawLines.join("\n"));
        persistWarehouseMd();

        state.focusTasks.unshift({
          id: makeId("cf"),
          text: wTask.text,
          why: "",
          status: "doing",
          isCore: false,
          notePath: null,
          subLabel: "",
          subTasks: []
        });
        persistDailyAnchor();
        renderAll();
        showToast("🔥 Đã đưa lên Trọng tâm hôm nay");
      }
    }
  });

  // =========================================================================
  // NATIVE CALLBACKS & LIVE SYNCTHING POLLING
  // =========================================================================
  window.__onVoiceState = function (listening) {
    els.btnVoice.classList.toggle("listening", !!listening);
  };

  window.__onVoiceText = function (txt) {
    if (!txt) return;
    els.composerInput.value = (els.composerInput.value ? els.composerInput.value + " " : "") + txt;
    els.composerInput.focus();
  };

  window.__reloadVaultFromNative = function () {
    loadVaultState();
    showToast("✓ Đã kết nối Vault Syncthing");
  };

  window.__openLaunchTab = function (tab, focusInput) {
    if (["quick", "notes", "task"].includes(tab)) {
      state.activeTab = tab;
      renderAll();
      if (focusInput) {
        setTimeout(() => els.composerInput.focus(), 120);
      }
    }
  };

  window.__onAppResumed = function () {
    if (!hasNative) return;
    const latest = window.AndroidBridge.getVaultModifiedTimestamp();
    if (latest !== state.lastKnownModified) {
      loadVaultState();
    }
  };

  window.__handleAndroidBack = function () {
    if (!els.bottomSheet.classList.contains("hidden")) {
      closeSheet();
      return true;
    }
    if (state.searchOpen) {
      els.btnSearchClear.click();
      return true;
    }
    if (state.activeTab !== "quick") {
      switchTab("quick");
      return true;
    }
    return false;
  };

  // Live poll every 2.2s while app is open so Syncthing edits from PC/Mac appear automatically
  setInterval(() => {
    if (!hasNative || document.hidden) return;
    try {
      const ts = window.AndroidBridge.getVaultModifiedTimestamp();
      if (ts > 0 && ts !== state.lastKnownModified) {
        loadVaultState();
      }
    } catch (_) {}
  }, 2200);

  loadVaultState();
})();

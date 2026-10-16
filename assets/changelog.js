// Loads Changelogs/index.json and renders the Markdown files listed there (same origin, no external requests).
(function () {
  var box = document.querySelector("[data-changelogs]");
  if (!box) return;

  function esc(s) {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  function inline(s) {
    return esc(s)
      .replace(/`([^`]+)`/g, "<code>$1</code>")
      .replace(/\*\*([^*]+)\*\*/g, "<b>$1</b>")
      .replace(/(^|[^*])\*([^*]+)\*/g, "$1<i>$2</i>")
      .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2">$1</a>');
  }

  function md(text) {
    var out = [], list = null, para = [];
    function flush() {
      if (para.length) out.push("<p>" + inline(para.join(" ")) + "</p>");
      para = [];
      if (list) out.push("</" + list + ">");
      list = null;
    }
    text.split(/\r?\n/).forEach(function (line, i) {
      var m;
      if (!line.trim()) return flush();
      if ((m = line.match(/^(#{1,4})\s+(.*)/))) {
        flush();
        if (i === 0 && m[1] === "#") return; // title is shown in the summary
        out.push("<h4>" + inline(m[2]) + "</h4>");
      } else if ((m = line.match(/^\s*([-*+]|\d+\.)\s+(.*)/))) {
        var type = /\d/.test(m[1]) ? "ol" : "ul";
        if (para.length || list !== type) flush();
        if (!list) out.push("<" + (list = type) + ">");
        out.push("<li>" + inline(m[2]) + "</li>");
      } else {
        if (list) flush();
        para.push(line.trim());
      }
    });
    flush();
    return out.join("");
  }

  function get(url, json) {
    return fetch(url, { cache: "no-cache" }).then(function (r) {
      if (!r.ok) throw new Error(r.status);
      return json ? r.json() : r.text();
    });
  }

  get("Changelogs/index.json", true)
    .then(function (list) {
      if (!list.length) {
        box.innerHTML = '<p class="sub">No changelogs yet.</p>';
        return;
      }
      return Promise.all(list.map(function (e) {
        return get("Changelogs/" + encodeURIComponent(e.file)).then(function (t) {
          return { title: e.title, html: md(t) };
        });
      })).then(function (items) {
        box.innerHTML = items.map(function (e, i) {
          return '<details class="xtx-log"' + (i === 0 ? " open" : "") + "><summary>" + esc(e.title) +
            (i === 0 ? ' <span class="xtx-new">Latest</span>' : "") + "</summary><div>" + e.html + "</div></details>";
        }).join("");
      });
    })
    .catch(function () {
      box.innerHTML = '<p class="sub">Changelogs could not be loaded. See them on ' +
        '<a href="https://github.com/RandomPixelStudios/X-To-Xray/tree/Website/Changelogs">GitHub</a>.</p>';
    });
})();

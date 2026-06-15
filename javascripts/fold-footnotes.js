// Wrap the auto-generated footnote block in a collapsible <details>, matching the
// `details.fold` style used for the later chapters. python-markdown hoists every
// [^x] definition into a single `div.footnote` at the end of the article, so it
// can't be folded in Markdown — we fold it here at load time instead.
(function () {
  function foldFootnotes() {
    var fn = document.querySelector(".md-typeset .footnote");
    if (!fn || fn.closest("details.footnote-fold")) return;

    var details = document.createElement("details");
    details.className = "fold footnote-fold";

    var summary = document.createElement("summary");
    var heading = document.getElementById("footnote-sources");
    summary.textContent = (heading && heading.textContent.trim()) || "Footnote sources";
    if (heading && heading.parentNode) heading.parentNode.removeChild(heading);
    details.appendChild(summary);

    fn.parentNode.insertBefore(details, fn);
    details.appendChild(fn);

    // Auto-open when a footnote ref (or back-ref) is navigated to.
    openForHash();
  }

  function openForHash() {
    if (!location.hash) return;
    var t = document.getElementById(decodeURIComponent(location.hash.slice(1)));
    var d = t && t.closest("details");
    if (d) d.open = true;
  }

  document.addEventListener("DOMContentLoaded", foldFootnotes);
  window.addEventListener("hashchange", openForHash);
})();

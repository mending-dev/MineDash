// Applies the saved theme before the first paint to avoid a flash
(function () {
  try {
    var saved = localStorage.getItem('minedash.theme');
    var dark = saved ? saved === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;
    document.documentElement.classList.toggle('dark', dark);
  } catch (e) {
    // Storage is blocked, the default theme stays
  }
})();
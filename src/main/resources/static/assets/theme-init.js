try {
  if (localStorage.getItem("codexonia-theme") === "dark") {
    document.documentElement.classList.add("dark");
  }
} catch (error) {
  /* Storage can be unavailable in private browsing modes. */
}

document.documentElement.classList.add("js");

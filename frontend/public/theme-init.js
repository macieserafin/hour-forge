// Applies the saved theme before the first paint (src/app/theme.ts keeps it in sync later).
try {
  var choice = localStorage.getItem('hourforge:theme') || 'system'
  var dark = choice === 'dark' || (choice === 'system' && matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
} catch (error) {
  /* Light until the app starts. */
}

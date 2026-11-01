/*
 * Tailwind is compiled ahead of time into
 * src/main/resources/static/assets/tailwind.css so the browser does not have to
 * download and run the Play CDN on every page.
 *
 * Rebuild after changing markup or utility classes:
 *   npx tailwindcss@3.4.17 -c tailwind.config.js -i tailwind.input.css -o src/main/resources/static/assets/tailwind.css --minify
 */
module.exports = {
  content: [
    "./src/main/resources/static/**/*.html",
    "./src/main/resources/static/assets/*.js"
  ],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        "primary-fixed": "#d7e2ff",
        "surface-container-lowest": "#ffffff",
        "on-primary-container": "#fcfbff",
        "secondary-fixed": "#f6d9ff",
        "secondary-container": "#d072ff",
        "inverse-surface": "#2f3034",
        "surface": "#faf8fe",
        "on-tertiary-fixed": "#002107",
        "primary-container": "#0071e3",
        "on-surface": "#1a1b1f",
        "on-error-container": "#93000a",
        "tertiary-fixed": "#72fe88",
        "secondary-fixed-dim": "#e8b3ff",
        "inverse-on-surface": "#f1f0f5",
        "surface-container": "#eeedf3",
        "primary": "#0059b5",
        "surface-variant": "#e3e2e7",
        "surface-bright": "#faf8fe",
        "background": "#faf8fe",
        "surface-container-high": "#e9e7ed",
        "outline": "#717785",
        "inverse-primary": "#abc7ff",
        "error-container": "#ffdad6",
        "on-primary-fixed": "#001b3f",
        "on-secondary-container": "#540079",
        "on-primary-fixed-variant": "#00458f",
        "error": "#ba1a1a",
        "on-primary": "#ffffff",
        "on-secondary-fixed": "#310048",
        "on-error": "#ffffff",
        "surface-dim": "#dad9df",
        "surface-container-highest": "#e3e2e7",
        "on-secondary": "#ffffff",
        "on-background": "#1a1b1f",
        "primary-fixed-dim": "#abc7ff",
        "on-tertiary-fixed-variant": "#00531c",
        "on-surface-variant": "#414753",
        "surface-container-low": "#f4f3f8",
        "on-tertiary-container": "#f1ffec",
        "secondary": "#8d2ebc",
        "outline-variant": "#c1c6d6",
        "tertiary-container": "#008633",
        "on-tertiary": "#ffffff",
        "surface-tint": "#005cbb",
        "on-secondary-fixed-variant": "#7201a2",
        "tertiary": "#006a26",
        "tertiary-fixed-dim": "#53e16f"
      },
      borderRadius: {
        DEFAULT: "0.25rem",
        lg: "0.5rem",
        xl: "0.75rem",
        full: "9999px"
      },
      spacing: {
        gutter: "1rem",
        "space-xs": "0.25rem",
        margin: "1.5rem",
        "space-lg": "1rem",
        "margin-mobile": "1rem",
        "space-xl": "1.5rem",
        "space-2xl": "2rem",
        "space-3xl": "3rem",
        "space-sm": "0.5rem",
        "gutter-compact": "0.5rem",
        "space-md": "0.75rem"
      },
      fontFamily: {
        "display-lg": ["Inter"],
        "body-sm": ["Inter"],
        "headline-lg": ["Inter"],
        "headline-md": ["Inter"],
        "label-code-md": ["JetBrains Mono"],
        "body-lg": ["Inter"],
        "display-lg-mobile": ["Inter"],
        "label-caps": ["Inter"],
        "body-md": ["Inter"],
        "label-code-lg": ["JetBrains Mono"],
        "label-code-sm": ["JetBrains Mono"]
      },
      fontSize: {
        "display-lg": ["34px", { lineHeight: "41px", letterSpacing: "-0.022em", fontWeight: "700" }],
        "body-sm": ["12px", { lineHeight: "16px", letterSpacing: "0em", fontWeight: "400" }],
        "headline-lg": ["22px", { lineHeight: "28px", letterSpacing: "-0.015em", fontWeight: "600" }],
        "headline-md": ["17px", { lineHeight: "22px", letterSpacing: "-0.012em", fontWeight: "600" }],
        "label-code-md": ["12px", { lineHeight: "16px", letterSpacing: "0em", fontWeight: "400" }],
        "body-lg": ["15px", { lineHeight: "20px", letterSpacing: "-0.008em", fontWeight: "400" }],
        "display-lg-mobile": ["28px", { lineHeight: "34px", letterSpacing: "-0.020em", fontWeight: "700" }],
        "label-caps": ["10px", { lineHeight: "12px", letterSpacing: "0.060em", fontWeight: "600" }],
        "body-md": ["13px", { lineHeight: "18px", letterSpacing: "-0.004em", fontWeight: "400" }],
        "label-code-lg": ["13px", { lineHeight: "18px", letterSpacing: "-0.010em", fontWeight: "500" }],
        "label-code-sm": ["11px", { lineHeight: "14px", letterSpacing: "0.010em", fontWeight: "400" }]
      }
    }
  }
};

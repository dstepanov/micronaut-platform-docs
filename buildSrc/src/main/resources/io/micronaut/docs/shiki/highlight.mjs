import { bundledLanguages, createHighlighter } from "shiki";
import { mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import { basename, join } from "node:path";

const LIGHT_THEME = "github-light-default";
const DARK_THEME = "github-dark-default";

const [inputDirectory, outputDirectory] = process.argv.slice(2);

if (!inputDirectory || !outputDirectory) {
  console.error("Usage: node highlight.mjs <input-directory> <output-directory>");
  process.exit(2);
}

const requestedLanguages = [
  "bash",
  "dockerfile",
  "groovy",
  "html",
  "java",
  "javascript",
  "json",
  "kotlin",
  "markdown",
  "properties",
  "python",
  "regexp",
  "shellscript",
  "sql",
  "toml",
  "typescript",
  "xml",
  "yaml"
].filter((language) => bundledLanguages[language]);

const highlighter = await createHighlighter({
  themes: [LIGHT_THEME, DARK_THEME],
  langs: requestedLanguages
});

const files = (await readdir(inputDirectory))
  .filter((file) => file.endsWith(".html"))
  .sort();

await mkdir(outputDirectory, { recursive: true });

for (const file of files) {
  const inputPath = join(inputDirectory, file);
  const outputPath = join(outputDirectory, basename(file));
  const slug = basename(file, ".html");
  const html = await readFile(inputPath, "utf8");
  const highlighted = highlightHtml(html);
  await writeFile(outputPath, highlighted, "utf8");
  await writeFile(
    join(outputDirectory, slug + ".js"),
    "window.__PLATFORM_DOCUMENTS__=window.__PLATFORM_DOCUMENTS__||{};"
      + "window.__PLATFORM_DOCUMENTS__[\"" + jsonString(slug) + "\"]="
      + JSON.stringify(highlighted)
      + ";\n",
    "utf8"
  );
}

function highlightHtml(html) {
  return html.replace(/<pre\b([^>]*)>\s*<code\b([^>]*)>([\s\S]*?)<\/code>\s*<\/pre>/gi, (match, preAttributes, codeAttributes, codeHtml) => {
    const language = shikiLanguage(codeAttributes);
    if (!language || !highlighter.getLoadedLanguages().includes(language)) {
      return normalizeCodeBlock(preAttributes, codeAttributes, codeHtml);
    }
    const { source, callouts } = codeSource(codeHtml);
    if (!source.trim()) {
      return match;
    }
    let highlighted = highlighter.codeToHtml(source, {
      lang: language,
      themes: {
        light: LIGHT_THEME,
        dark: DARK_THEME
      },
      defaultColor: false
    });
    highlighted = highlighted.replace("<code>", "<code" + shikiCodeAttributes(codeAttributes) + ">");
    callouts.forEach((callout) => {
      highlighted = highlighted.replaceAll(callout.marker, callout.html);
    });
    return highlighted;
  });
}

function shikiLanguage(attributes) {
  const classLanguage = languageClass(attributes);
  const dataLanguage = attribute(attributes, "data-lang");
  const language = normalizeLanguage(classLanguage || dataLanguage || "");
  if (!language || ["nohighlight", "plain", "plaintext", "text"].includes(language)) {
    return "";
  }
  return language;
}

function languageClass(attributes) {
  return /\blanguage-([A-Za-z0-9_-]+)\b/.exec(attributes)?.[1] || "";
}

function attribute(attributes, name) {
  return new RegExp("\\b" + escapeRegExp(name) + "\\s*=\\s*\"([^\"]*)\"", "i").exec(attributes)?.[1] || "";
}

function normalizeLanguage(language) {
  const normalized = language.toLowerCase();
  const aliases = {
    console: "bash",
    "gradle-groovy": "groovy",
    "gradle-kotlin": "kotlin",
    "groovy-config": "groovy",
    hocon: "json",
    "json-config": "json",
    maven: "xml",
    regex: "regexp",
    sh: "bash",
    shell: "bash",
    yml: "yaml",
    zsh: "bash"
  };
  return aliases[normalized] || normalized;
}

function codeSource(codeHtml) {
  const callouts = [];
  const withMarkers = codeHtml.replace(/<(b|i)\b[^>]*\bclass\s*=\s*("[^"]*\bconum\b[^"]*"|'[^']*\bconum\b[^']*')[^>]*>[\s\S]*?<\/\1>/gi, (html) => {
    const marker = "MNDOCSCALLOUT" + callouts.length + "MARKER";
    callouts.push({ marker, html });
    return marker;
  });
  return {
    source: decodeHtml(withMarkers.replace(/<[^>]+>/g, "")),
    callouts
  };
}

function normalizeCodeBlock(preAttributes, codeAttributes, codeHtml) {
  return "<pre" + normalizedAttributes(preAttributes, "docs-code-pre", ["highlightjs", "highlight", "hljs"]) + ">"
    + "<code" + normalizedAttributes(codeAttributes, "", ["hljs"]) + ">"
    + codeHtml
    + "</code></pre>";
}

function normalizedAttributes(attributes, requiredClass, removedClasses) {
  const withoutClass = attributes.replace(/\s*\bclass\s*=\s*"[^"]*"/i, "");
  const classes = attribute(attributes, "class")
    .split(/\s+/)
    .filter((className) => className && !removedClasses.includes(className));
  if (requiredClass && !classes.includes(requiredClass)) {
    classes.push(requiredClass);
  }
  return classes.length ? " class=\"" + escapeAttribute(classes.join(" ")) + "\"" + withoutClass : withoutClass;
}

function shikiCodeAttributes(attributes) {
  const withoutClass = attributes.replace(/\s*\bclass\s*=\s*"[^"]*"/i, "");
  const classValue = attribute(attributes, "class")
    .split(/\s+/)
    .filter((className) => className && className !== "hljs")
    .concat("shiki-code")
    .join(" ");
  return " class=\"" + escapeAttribute(classValue) + "\"" + withoutClass;
}

function decodeHtml(value) {
  return value
    .replace(/&#x([0-9a-f]+);/gi, (_match, hex) => String.fromCodePoint(Number.parseInt(hex, 16)))
    .replace(/&#([0-9]+);/g, (_match, decimal) => String.fromCodePoint(Number.parseInt(decimal, 10)))
    .replace(/&quot;/g, "\"")
    .replace(/&apos;/g, "'")
    .replace(/&#39;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&");
}

function escapeAttribute(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("\"", "&quot;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function jsonString(value) {
  return value
    .replaceAll("\\", "\\\\")
    .replaceAll("\"", "\\\"");
}

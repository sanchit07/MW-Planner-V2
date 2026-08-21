#!/usr/bin/env node
/**
 * Markdown → Confluence storage-format converter for the MW Planner PRD.
 *
 * Reads docs/PRD_MW_PLANNER.md and writes the storage-format mirror at
 * docs/confluence-payload-mw-planner-prd.xml. Handles headings, paragraphs,
 * horizontal rules, GFM tables (header row → <th>, separator row dropped,
 * body rows → <td>), fenced code blocks (→ Confluence code macro), ordered
 * and unordered lists, and inline formatting (bold, italic, inline code,
 * links). A clickable Table of Contents macro is injected after the intro.
 *
 * Re-run after editing the PRD so the two files stay in sync:
 *   node scripts/markdown-to-confluence.mjs
 */
import fs from 'node:fs/promises';

const SOURCE = process.env.PRD_SOURCE_PATH || 'docs/PRD_MW_PLANNER.md';
const TARGET =
  process.env.CONFLUENCE_PAYLOAD_PATH ||
  'docs/confluence-payload-mw-planner-prd.xml';
const PAGE_ID = process.env.CONFLUENCE_PAGE_ID || '66060301';

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/** Convert inline markdown (after HTML-escaping) to storage-format spans. */
function inline(raw) {
  let s = escapeHtml(raw);
  // Links [text](url) — do this before emphasis so URLs are untouched.
  s = s.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_m, text, url) => `<a href="${url}">${text}</a>`);
  // Inline code — before emphasis so asterisks inside code are preserved.
  s = s.replace(/`([^`]+)`/g, (_m, code) => `<code>${code}</code>`);
  // Bold then italic.
  s = s.replace(/\*\*([^*]+?)\*\*/g, '<strong>$1</strong>');
  s = s.replace(/\*([^*]+?)\*/g, '<em>$1</em>');
  return s;
}

function isTableRow(line) {
  return /^\|.*\|\s*$/.test(line);
}
function isTableSeparator(line) {
  return /^\|[\s:|-]+\|\s*$/.test(line) && /-/.test(line);
}
function splitRow(line) {
  // Drop leading/trailing pipe, split on unescaped pipes, trim each cell.
  const trimmed = line.trim().replace(/^\|/, '').replace(/\|$/, '');
  return trimmed.split('|').map((c) => c.trim());
}

function convert(md) {
  const lines = md.replace(/\r\n/g, '\n').split('\n');
  const out = [];
  let tocInserted = false;
  let i = 0;

  const insertTocBeforeFirstHr = () => {
    if (!tocInserted) {
      out.push(
        '<ac:structured-macro ac:name="toc"><ac:parameter ac:name="minLevel">2</ac:parameter><ac:parameter ac:name="maxLevel">3</ac:parameter></ac:structured-macro>',
      );
      tocInserted = true;
    }
  };

  while (i < lines.length) {
    let line = lines[i];

    // Blank line — skip.
    if (/^\s*$/.test(line)) {
      i++;
      continue;
    }

    // Fenced code block.
    if (/^```/.test(line)) {
      const fence = line.trim();
      const lang = fence.replace(/^```/, '').trim();
      const buf = [];
      i++;
      while (i < lines.length && !/^```/.test(lines[i])) {
        buf.push(lines[i]);
        i++;
      }
      i++; // consume closing fence
      const langParam = lang
        ? `<ac:parameter ac:name="language">${escapeHtml(lang)}</ac:parameter>`
        : '';
      out.push(
        `<ac:structured-macro ac:name="code">${langParam}<ac:plain-text-body><![CDATA[${buf.join('\n')}]]></ac:plain-text-body></ac:structured-macro>`,
      );
      continue;
    }

    // Horizontal rule.
    if (/^---+\s*$/.test(line) || /^\*\*\*+\s*$/.test(line)) {
      insertTocBeforeFirstHr();
      out.push('<hr />');
      i++;
      continue;
    }

    // Heading.
    const h = line.match(/^(#{1,6})\s+(.*)$/);
    if (h) {
      const level = h[1].length;
      out.push(`<h${level}>${inline(h[2].trim())}</h${level}>`);
      i++;
      continue;
    }

    // Table.
    if (isTableRow(line) && i + 1 < lines.length && isTableSeparator(lines[i + 1])) {
      const header = splitRow(line);
      i += 2; // skip header + separator
      const bodyRows = [];
      while (i < lines.length && isTableRow(lines[i])) {
        bodyRows.push(splitRow(lines[i]));
        i++;
      }
      const head = `<tr>${header.map((c) => `<th>${inline(c)}</th>`).join('')}</tr>`;
      const body = bodyRows
        .map((cells) => `<tr>${cells.map((c) => `<td>${inline(c)}</td>`).join('')}</tr>`)
        .join('\n');
      out.push(`<table>\n<tbody>\n${head}\n${body}\n</tbody>\n</table>`);
      continue;
    }

    // Lists (unordered / ordered). Collect a contiguous run of same type.
    const ulItem = line.match(/^[-*]\s+(.*)$/);
    const olItem = line.match(/^\d+\.\s+(.*)$/);
    if (ulItem || olItem) {
      const ordered = !!olItem;
      const items = [];
      while (i < lines.length) {
        const m = ordered
          ? lines[i].match(/^\d+\.\s+(.*)$/)
          : lines[i].match(/^[-*]\s+(.*)$/);
        if (!m) break;
        items.push(`<li>${inline(m[1].trim())}</li>`);
        i++;
      }
      const tag = ordered ? 'ol' : 'ul';
      out.push(`<${tag}>\n${items.join('\n')}\n</${tag}>`);
      continue;
    }

    // Paragraph — single line (the PRD uses one line per paragraph).
    out.push(`<p>${inline(line.trim())}</p>`);
    i++;
  }

  return out.join('\n');
}

const header = `<!--
    Confluence storage-format payload — MW Planner PRD
    ===================================================
    Source: ${SOURCE}
    Target: Confluence page id ${PAGE_ID} (MW Planner — Product Requirements Document)
    Generated: ${new Date().toISOString()}

    HOW TO PASTE
    ------------
    1. Open the target Confluence page in edit mode.
    2. Click the "..." menu and choose "View source" (storage-format editor).
    3. Select all and replace with the contents of this file BELOW the closing
       comment marker on the next line. Do not include this header comment.
    4. Save. Confluence will render headings, tables, code blocks, the table of
       contents macro and links natively.

    Notes
    -----
    • Generated by scripts/markdown-to-confluence.mjs.
    • Re-run with: node scripts/markdown-to-confluence.mjs
    • Keep this file in sync whenever ${SOURCE} changes.
  -->`;

const md = await fs.readFile(SOURCE, 'utf8');
const body = convert(md);
await fs.writeFile(TARGET, `${header}\n${body}\n`, 'utf8');
console.log(`Wrote ${TARGET} (${body.length} chars of storage format)`);

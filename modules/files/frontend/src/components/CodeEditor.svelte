<script lang="ts">
  import { onMount, untrack } from 'svelte';
  import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands';
  import { json } from '@codemirror/lang-json';
  import { xml } from '@codemirror/lang-xml';
  import { yaml } from '@codemirror/lang-yaml';
  import {
    bracketMatching,
    foldGutter,
    foldKeymap,
    HighlightStyle,
    indentOnInput,
    indentUnit,
    StreamLanguage,
    syntaxHighlighting
  } from '@codemirror/language';
  import { properties } from '@codemirror/legacy-modes/mode/properties';
  import { shell } from '@codemirror/legacy-modes/mode/shell';
  import { toml } from '@codemirror/legacy-modes/mode/toml';
  import { highlightSelectionMatches, openSearchPanel, searchKeymap } from '@codemirror/search';
  import { Compartment, EditorState, type Extension, type Text } from '@codemirror/state';
  import {
    drawSelection,
    dropCursor,
    EditorView,
    highlightActiveLine,
    highlightActiveLineGutter,
    highlightSpecialChars,
    keymap,
    lineNumbers,
    type ViewUpdate
  } from '@codemirror/view';
  import { tags as t } from '@lezer/highlight';
  import type { Snapshot } from '../lib/editor.svelte';
  import { languageKind, type LanguageKind } from '../lib/languages';

  let {
    text,
    separator,
    fileName,
    draft = null,
    wrap = false,
    readOnly = false,
    onchange,
    onposition,
    onunmount
  }: {
    /** Content of the file. It is only read when the editor is created. */
    text: string;
    /** Line break of the file. The editor splits lines at exactly this, so the file is saved unchanged. */
    separator: string;
    fileName: string;
    /** Unsaved text that is placed on top of the content right away. */
    draft?: string | null;
    wrap?: boolean;
    readOnly?: boolean;
    /** Called after every change of the text, with the unsaved state after that change. */
    onchange: (dirty: boolean) => void;
    onposition: (line: number, column: number) => void;
    /** Called right before the editor is destroyed, and only if there is unsaved text. */
    onunmount?: (snapshot: Snapshot) => void;
  } = $props();

  let host = $state<HTMLDivElement>();
  let view: EditorView | undefined;
  /** The document as it is on disk. The file is dirty as long as the editor shows something else. */
  let savedDoc: Text | undefined;

  const wrapCompartment = new Compartment();
  const editableCompartment = new Compartment();

  // Colors come from the panel, so the editor follows the light and dark theme without any code
  const highlightStyle = HighlightStyle.define([
    {
      tag: [t.comment, t.lineComment, t.blockComment, t.docComment],
      color: 'var(--md-muted)',
      fontStyle: 'italic'
    },
    {
      tag: [
        t.propertyName,
        t.definition(t.propertyName),
        t.definition(t.variableName),
        t.attributeName,
        t.tagName,
        t.typeName,
        t.className
      ],
      color: 'var(--md-accent)'
    },
    {
      tag: [t.keyword, t.operatorKeyword, t.controlKeyword, t.definitionKeyword, t.modifier],
      color: 'var(--md-accent)',
      fontWeight: '600'
    },
    { tag: [t.string, t.special(t.string), t.regexp], color: 'var(--md-info)' },
    { tag: [t.number, t.bool, t.null, t.atom], color: 'var(--md-warning)' },
    { tag: [t.meta, t.processingInstruction, t.separator], color: 'var(--md-muted)' },
    { tag: t.invalid, color: 'var(--md-danger)' },
    { tag: [t.heading, t.strong], fontWeight: '700' },
    { tag: t.emphasis, fontStyle: 'italic' },
    { tag: t.link, textDecoration: 'underline' }
  ]);

  const theme = EditorView.theme({
    '&': {
      height: '100%',
      color: 'var(--md-text)',
      backgroundColor: 'var(--md-bg)',
      fontSize: '0.8125rem'
    },
    '&.cm-focused': { outline: 'none' },
    '.cm-scroller': { fontFamily: 'var(--md-font-mono)', lineHeight: '1.55', overflow: 'auto' },
    '.cm-content': { caretColor: 'var(--md-accent)', padding: '0.5rem 0' },
    '.cm-cursor, .cm-dropCursor': { borderLeftColor: 'var(--md-accent)', borderLeftWidth: '2px' },
    '&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection':
      { backgroundColor: 'color-mix(in srgb, var(--md-accent) 28%, transparent)' },
    '.cm-activeLine': { backgroundColor: 'color-mix(in srgb, var(--md-accent) 7%, transparent)' },
    '.cm-gutters': {
      backgroundColor: 'var(--md-surface)',
      color: 'var(--md-muted)',
      border: 'none',
      borderRight: '1px solid var(--md-border)'
    },
    '.cm-activeLineGutter': {
      backgroundColor: 'color-mix(in srgb, var(--md-accent) 10%, transparent)',
      color: 'var(--md-text)'
    },
    '.cm-foldPlaceholder': {
      backgroundColor: 'var(--md-surface-2)',
      border: '1px solid var(--md-border)',
      color: 'var(--md-muted)'
    },
    '&.cm-focused .cm-matchingBracket, &.cm-focused .cm-nonmatchingBracket': {
      backgroundColor: 'color-mix(in srgb, var(--md-accent) 25%, transparent)',
      outline: 'none'
    },
    '.cm-selectionMatch': { backgroundColor: 'color-mix(in srgb, var(--md-info) 22%, transparent)' },
    '.cm-searchMatch': {
      backgroundColor: 'color-mix(in srgb, var(--md-warning) 30%, transparent)',
      outline: '1px solid var(--md-warning)'
    },
    '.cm-searchMatch.cm-searchMatch-selected': {
      backgroundColor: 'color-mix(in srgb, var(--md-warning) 55%, transparent)'
    },
    '.cm-specialChar': { color: 'var(--md-danger)' },
    '.cm-panels': { backgroundColor: 'var(--md-surface)', color: 'var(--md-text)' },
    '.cm-panels.cm-panels-top': { borderBottom: '1px solid var(--md-border)' },
    '.cm-panel.cm-search': { padding: '0.5rem 2rem 0.5rem 0.75rem' },
    '.cm-textfield': {
      backgroundColor: 'var(--md-bg)',
      color: 'var(--md-text)',
      border: '1px solid var(--md-border)',
      borderRadius: '0.375rem',
      padding: '0.25rem 0.5rem'
    },
    '.cm-button': {
      backgroundImage: 'none',
      backgroundColor: 'var(--md-surface-2)',
      color: 'var(--md-text)',
      border: '1px solid var(--md-border)',
      borderRadius: '0.375rem'
    },
    '.cm-panel.cm-search [name=close]': { color: 'var(--md-muted)' },
    '.cm-tooltip': {
      backgroundColor: 'var(--md-surface)',
      color: 'var(--md-text)',
      border: '1px solid var(--md-border)'
    }
  });

  function languageExtension(kind: LanguageKind | null): Extension {
    switch (kind) {
      case 'json':
        return json();
      case 'yaml':
        return yaml();
      case 'xml':
        return xml();
      case 'properties':
        return StreamLanguage.define(properties);
      case 'toml':
        return StreamLanguage.define(toml);
      case 'shell':
        return StreamLanguage.define(shell);
      default:
        return [];
    }
  }

  function editableExtension(locked: boolean): Extension {
    return [EditorState.readOnly.of(locked), EditorView.editable.of(!locked)];
  }

  function createState(): EditorState {
    return EditorState.create({
      doc: text,
      extensions: [
        lineNumbers(),
        highlightActiveLineGutter(),
        // Shows control characters, so nothing in the file is invisible
        highlightSpecialChars(),
        history(),
        foldGutter(),
        drawSelection(),
        dropCursor(),
        indentOnInput(),
        syntaxHighlighting(highlightStyle),
        bracketMatching(),
        highlightActiveLine(),
        highlightSelectionMatches(),
        keymap.of([...defaultKeymap, ...searchKeymap, ...historyKeymap, ...foldKeymap, indentWithTab]),
        EditorState.tabSize.of(2),
        indentUnit.of('  '),
        EditorState.lineSeparator.of(separator),
        languageExtension(languageKind(fileName)),
        wrapCompartment.of(wrap ? EditorView.lineWrapping : []),
        editableCompartment.of(editableExtension(readOnly)),
        theme,
        EditorView.updateListener.of(onUpdate)
      ]
    });
  }

  function onUpdate(update: ViewUpdate): void {
    if (update.docChanged) reportDirty(update.state.doc);
    if (update.docChanged || update.selectionSet) reportPosition(update.state);
  }

  function reportDirty(doc: Text): void {
    // Text.eq stops at the first difference, so this stays cheap while typing
    onchange(savedDoc !== undefined && !doc.eq(savedDoc));
  }

  function reportPosition(state: EditorState): void {
    const head = state.selection.main.head;
    const line = state.doc.lineAt(head);
    onposition(line.number, head - line.from + 1);
  }

  /** The text as it has to be saved, plus a marker of the exact document it was taken from. */
  export function getSnapshot(): Snapshot {
    const state = view!.state;
    return {
      text: state.doc.sliceString(0, state.doc.length, state.lineBreak),
      token: state.doc
    };
  }

  /** Tells the editor that this snapshot is now on disk. Edits made since then stay unsaved. */
  export function markSaved(snapshot: Snapshot): void {
    savedDoc = snapshot.token as Text;
    if (view) reportDirty(view.state.doc);
  }

  export function find(): void {
    if (view) openSearchPanel(view);
  }

  export function focus(): void {
    view?.focus();
  }

  onMount(() => {
    const parentElement = host;
    if (!parentElement) return;

    const editorView = new EditorView({ state: createState(), parent: parentElement });
    view = editorView;
    savedDoc = editorView.state.doc;

    if (draft !== null) {
      editorView.dispatch({ changes: { from: 0, to: editorView.state.doc.length, insert: draft } });
    }
    reportDirty(editorView.state.doc);
    reportPosition(editorView.state);

    // On touch devices focusing would raise the keyboard uninvited
    if (window.matchMedia('(pointer: fine)').matches) {
      editorView.focus();
    }

    return () => {
      // Unsaved text is handed over before the editor disappears, e.g. when the user opens another page of the panel
      if (savedDoc !== undefined && !editorView.state.doc.eq(savedDoc)) {
        try {
          onunmount?.(getSnapshot());
        } catch {
          // A failing hook must never keep the editor from being destroyed
        }
      }
      editorView.destroy();
      view = undefined;
    };
  });

  // The editor is not rebuilt for these, the settings are swapped in place
  $effect(() => {
    const enabled = wrap;
    untrack(() => {
      view?.dispatch({ effects: wrapCompartment.reconfigure(enabled ? EditorView.lineWrapping : []) });
    });
  });

  $effect(() => {
    const locked = readOnly;
    untrack(() => {
      view?.dispatch({ effects: editableCompartment.reconfigure(editableExtension(locked)) });
    });
  });
</script>

<div class="host" bind:this={host}></div>

<style>
  .host {
    height: 100%;
    min-height: 0;
  }

  /* 16px keeps mobile Safari from zooming in when the editor gets the focus */
  @media (max-width: 40rem) {
    .host :global(.cm-editor) {
      font-size: 1rem;
    }
  }
</style>
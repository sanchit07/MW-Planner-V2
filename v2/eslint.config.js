// eslint.config.js
import js from "@eslint/js";
import globals from "globals";
import react from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";
import importPlugin from "eslint-plugin-import";
import tseslint from "typescript-eslint";
import prettierPlugin from "eslint-plugin-prettier";


export default [
  { ignores: ["dist/", "build/", "coverage/", "**/*.min.js"] },

  js.configs.recommended,
  ...tseslint.configs.recommended, // use recommendedTypeChecked only if you wire project

  {
    files: ["src/**/*.{ts,tsx}"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      globals: { ...globals.browser, ...globals.node },
      parserOptions: {
        ecmaFeatures: { jsx: true },
        // For type-aware rules:
        // project: ['./tsconfig.json'],
        // tsconfigRootDir: new URL('.', import.meta.url)
      },
    },
    plugins: {
      react,
      "react-hooks": reactHooks,
      import: importPlugin,
      prettier: prettierPlugin,
    },
    settings: { react: { version: "detect" } },
    rules: {
      "react/react-in-jsx-scope": "off",
      "react/jsx-uses-react": "off",
      "react/prop-types": "off",
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "warn",
      "import/order": [
        "warn",
        {
          groups: [
            "builtin",
            "external",
            "internal",
            ["parent", "sibling", "index"],
          ],
          "newlines-between": "always",
          alphabetize: { order: "asc", caseInsensitive: true },
        },
      ],
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
      ],
      "prettier/prettier": "warn",
    },
  },

  // JS/JSX in TS repo (optional)
  {
    files: ["src/**/*.{js,jsx}"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      globals: { ...globals.browser, ...globals.node },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    plugins: {
      react,
      "react-hooks": reactHooks,
      import: importPlugin,
      prettier: prettierPlugin,
    },
    settings: { react: { version: "detect" } },
    rules: {
      "react/react-in-jsx-scope": "off",
      "react/jsx-uses-react": "off",
      "react/prop-types": "off",
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "warn",
      "prettier/prettier": "warn",
    },
  },

  // Tests
  {
    files: ["**/*.{test,spec}.{ts,tsx,js,jsx}"],
    languageOptions: { globals: { ...globals.jest, ...globals.node } },
  },

  // Node scripts
  {
    files: ["*.{cjs,mjs,js}", "scripts/**/*.{js,ts}"],
    languageOptions: { globals: globals.node },
  },
];

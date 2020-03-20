/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// See https://docusaurus.io/docs/site-config for all the possible
// site configuration options.

const repoUrl = "https://github.com/iRevive/fmq";

const siteConfig = {
    title: "ƒMQ",
    tagline: "Functional bindings for ZeroMQ library",
    url: 'https://irevive.github.io', // Your website URL
    baseUrl: '/fmq/',
    projectName: "fmq",
    organizationName: "iRevive",

    // For no header links in the top nav bar -> headerLinks: [],
    headerLinks: [
        { href: repoUrl, label: "GitHub", external: true },
        { href: 'https://www.javadoc.io/doc/io.github.irevive/fmq-core_2.13/latest/index.html', label: 'Documentation', external: true },
    ],

    customDocsPath: "fmq-docs/target/mdoc",

    /* Colors for website */
    colors: {
        primaryColor: '#2e8555',
        secondaryColor: '#20232a',
    },

    // This copyright info is used in /core/Footer.js and blog RSS/Atom feeds.
    copyright: `Copyright © ${new Date().getFullYear()} irevive.github.io`,

    highlight: {
        // Highlight.js theme to use for syntax highlighting in code blocks.
        theme: 'github',
    },

    // On page navigation for the current documentation page.
    onPageNav: 'separate',

    // For sites with a sizable amount of content, set collapsible to true.
    // Expand/collapse the links and subcategories under categories.
    // docsSideNavCollapsible: true,

    // Show documentation's last contributor's name.
    // enableUpdateBy: true,

    // Show documentation's last update time.
    enableUpdateTime: true,

    // You may provide arbitrary config keys to be used as needed by your
    // template. For example, if you need your repo's URL...
    repoUrl: repoUrl,
};

module.exports = siteConfig;

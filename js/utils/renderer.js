// ==========================================================
// BRasa
// Renderer
// ==========================================================

/**
 * Atualiza os ícones Lucide após alterações no DOM.
 */

let iconRefreshQueued = false;

function refreshIcons() {

    if (window.lucide) {

        if (iconRefreshQueued) return;

        iconRefreshQueued = true;

        window.requestAnimationFrame(() => {
            iconRefreshQueued = false;
            lucide.createIcons();
        });

    }

}

/**
 * Localiza um elemento no DOM.
 */

function getElement(selector) {

    const element = document.querySelector(selector);

    if (!element) {

        console.error(`Elemento "${selector}" não encontrado.`);

        return null;

    }

    return element;

}

/**
 * Substitui completamente o conteúdo.
 */

export function render(selector, component) {

    const element = getElement(selector);

    if (!element) return;

    element.innerHTML = component;

    refreshIcons();

}

/**
 * Adiciona conteúdo ao final.
 */

export function append(selector, component) {

    const element = getElement(selector);

    if (!element) return;

    element.insertAdjacentHTML(

        "beforeend",

        component

    );

    refreshIcons();

}

/**
 * Adiciona conteúdo no início.
 */

export function prepend(selector, component) {

    const element = getElement(selector);

    if (!element) return;

    element.insertAdjacentHTML(

        "afterbegin",

        component

    );

    refreshIcons();

}

/**
 * Renderiza vários componentes de uma única vez.
 */

export function renderMany(selector, components = []) {

    const element = getElement(selector);

    if (!element) return;

    element.innerHTML = components.join("");

    refreshIcons();

}

/**
 * Remove todo o conteúdo.
 */

export function clear(selector) {

    const element = getElement(selector);

    if (!element) return;

    element.innerHTML = "";

}

/**
 * Remove um elemento do DOM.
 */

export function remove(selector) {

    const element = getElement(selector);

    if (!element) return;

    element.remove();

}

/**
 * Substitui um componente por outro.
 */

export function replace(selector, component) {

    render(selector, component);

}

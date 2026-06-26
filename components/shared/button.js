export default function Button({
    text = "Botão",
    icon = "",
    type = "button",
    variant = "primary",
    id = "",
    classes = ""
} = {}) {

    return `
        <button
            type="${type}"
            id="${id}"
            class="btn btn--${variant} ${classes}"
        >

            ${icon ? `<span class="btn__icon">${icon}</span>` : ""}

            <span class="btn__text">
                ${text}
            </span>

        </button>
    `;

}
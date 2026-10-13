function parse(): string {
    const hash = location.hash.replace(/^#/, '');
    return hash.startsWith('/') ? hash : '/';
}

class Router {
    path = $state(parse());

    constructor() {
        window.addEventListener('hashchange', () => {
            this.path = parse();
        });
    }
}

export const router = new Router();
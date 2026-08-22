import { Store } from './store.js';

export function createStore() {
  return new Store(process.env.DATABASE_URL);
}

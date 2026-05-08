export default {
  preset: 'react-native-harness',
  roots: ['<rootDir>/harness'],
  testMatch: ['<rootDir>/harness/**/*.harness.{js,ts,tsx}'],
  testPathIgnorePatterns: ['<rootDir>/node_modules/', '<rootDir>/../../node_modules/', '<rootDir>/../../lib/'],
  watchman: false,
};

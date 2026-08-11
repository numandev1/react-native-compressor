all:

ROOT_DIR    := $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
BARE_DIR    := $(ROOT_DIR)/examples/bare
EXPO_DIR    := $(ROOT_DIR)/examples/expo
VERSION     := $(shell awk '/version/{gsub(/("|",)/,"",$$2);print $$2};' package.json)

install: # Yarn install (root + example workspaces)
	yarn install

pod-install: # Pod install for the bare example
	cd $(BARE_DIR) && bundle install
	cd $(BARE_DIR) && bundle exec pod install --project-directory=ios/ --repo-update

sync: install pod-install

nitrogen: # Regenerate Nitro native bindings into nitrogen/generated/
	yarn nitrogen

prepack: # nitrogen + build the publishable lib/
	yarn prepack

run-ios: # Run the bare example on an iOS device
	yarn example:bare ios

run-ios-sim: # Run the bare example on an iOS simulator
	IOS_SIMULATOR="iPhone 17 Pro" yarn example:bare ios

run-android: # Run the bare example on an Android device or emulator
	yarn example:bare android

run-bundler: # Run metro bundler for the bare example
	watchman watch-del $(ROOT_DIR)
	watchman watch-project $(ROOT_DIR)
	yarn example:bare start --reset-cache

run-expo-ios: # Run the expo example on iOS
	yarn example:expo ios

run-expo-android: # Run the expo example on Android
	yarn example:expo android

run-expo-bundler: # Run the expo bundler
	yarn example:expo start

build-android: # Native Android compile check (arm64-v8a)
	yarn build:android

build-ios: # Native iOS compile check (simulator)
	yarn build:ios

test: # Jest unit tests (native mocked)
	yarn test

typecheck: # tsc --noEmit
	yarn typecheck

lint: # eslint over **/*.{js,ts,tsx}
	yarn lint

lint-fix: # eslint --fix
	yarn lint --fix

test-pr: # Full PR gate: test + typecheck + lint
	yarn test:pr

test-harness-android: # On-device harness tests (requires an Android emulator)
	yarn test:harness:android

test-harness-ios: # On-device harness tests (requires an iOS simulator)
	yarn test:harness:ios

clean: # Clean native build artifacts
	yarn clean

clean-deps: # Remove lockfile-installed dependencies
	rm -rf node_modules
	rm -rf $(BARE_DIR)/node_modules
	rm -rf $(EXPO_DIR)/node_modules

purge: # Deep clean repository (removes all untracked files/dirs)
	git clean -xdf

refresh: clean-deps install pod-install

resync: purge install pod-install # Full reinstall with deep clean

open-android:
	open -a /Applications/Android\ Studio.app

open-ios:
	open $(BARE_DIR)/ios/BareExample.xcworkspace

check-dep:
	yarn outdated

release: # Cut a release (release-it + conventional-changelog)
	yarn release
MVN = ./mvnw

.PHONY: release

## Create a new release (usage: make release NEW_VERSION=x.y.z)
release:
ifndef NEW_VERSION
	$(error NEW_VERSION is required. Usage: make release NEW_VERSION=x.y.z)
endif
	$(MVN) versions:set-property -Dproperty=revision -DnewVersion=$(NEW_VERSION)
	$(MVN) versions:commit
	git commit -am "[release] $(NEW_VERSION)"
	git tag $(NEW_VERSION)
	@echo "Release $(NEW_VERSION) created. Push with: git push origin main --tags"

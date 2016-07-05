# lightblue-metadata-manager
A command line tool to manage [Lightblue](https://github.com/lightblue-platform/) metadata.

Workflow:

1. Pull metadata from Lightblue,
2. make changes, review diff,
3. push changes back to Lightblue.

Yes, it's similar to working in git.

## Usage:
```
usage: MetadataManagerApp$ <operation> <options>

Available operations: list, pull, push, diff.

Options:
 -e,--entity <entity name or /regex/>                   Entity name. You can use regular expression to match multiple entities by name.
    --env <environment, e.g. dev>                       Lightblue environment (export LB_CLIENT_{ENV}=/home/user/lightblue-clients/lightblue-client-{ENV}.properties
 -h,--help                                              prints usage
    --ignoreHooks                                       Don't push hooks.
    --ignoreIndexes                                     Don't push indexes.
 -lc,--lightblue-client <lightblue-client.properties>   Configuration file for lightblue-client. --env is recommended instead.
 -v,--version <x.x.x|newest|default>                    Entity version selector.

```

## Installation

For rpm based distributions, install from rpm (mvn clean install to build the rpm).

Once binary is installed, configure Lightblue environments. You can create as many environments as you like, e.g.:

```
export LB_CLIENT_DEV=/home/<user>/lightblue-clients/lightblue-client-dev.properties
export LB_CLIENT_QA=/home/<user>/lightblue-clients/lightblue-client-qa.properties
export LB_CLIENT_PROD=/home/<user>/lightblue-clients/lightblue-client-prod.properties
```
(see [lightblue-client](https://github.com/lightblue-platform/lightblue-client) for information on client configuration)

Now verify your installation:
```
lbmd list --env dev
```
Should print a list of all entities.


## Examples

### Make changes in metadata

```
lbmd pull --env dev -e user -v newest # saves newest user.json version in your current directory
vim user.json # make changes in user metadata
lbmd diff --env dev -e user # diff your local user copy against newest user version in Lightblue
lbmd push --env dev -e user # Update metadata in Lightblue in dev
lbmd push --env qa -e user # Update metadata in Lightblue in qa
```

### Download multiple entities
```
lbmd pull --env dev -e "/.*/" -v newest # download all newest versions
lbmd pull --env dev -e "/^(user|legalEntity).*/" -v newest # download all entities starting with user or legalEntity entity
```

## Debug

See /tmp/lightblue-metadata-manager-debug.log. This is where all debug statements are logged, including lightblue-client logs.

## TODOs
* Update versions, including dependencies in other entities
* Push only entityInfo or only schema
* Push in bulk
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

Available operations: list, pull, push, diff and set. Add -h after operation to see options it accepts.

Options:
 -h,--help   Prints usage.
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

### Merge metadata

Say you want to promote metadata from stage to prod, including entityInfo, but you want to make sure you don't change any indexes in prod.
```
lbmd pull --env stage -e user -v newest # saves newest user.json version in stage in your current directory
lbmd pull --env prod -e user -v newest --path entityInfo.indexes # Merge entityInfo.indexes from prod into your local copy
lbmd diff --env prod -e user # diff your local copy against prod to see what will get pushed
lbmd push --env prod -e user # update metadata in prod
```

### More pull examples
```
lbmd pull --env dev -e user -v 0.0.2 # download user entity version 0.0.2
lbmd pull --env dev -e user -v default # download default user entity version
lbmd pull --env dev -e \$all -v newest # download all newest versions
lbmd pull --env dev -e \$local -v newest # refresh all entities in local working directory with remote newest versions
lbmd pull --env dev -e "/^(user|legalEntity).*/" -v newest # download all entities starting with user or legalEntity entity
```

### More push examples
```
lbmd push --env qa -e user --schemaOnly # upload schema only
lbmd push --env qa -e user --entityInfoOnly # upload entityInfoOnly
lbmd push --env qa -e \$all # upload all entities in your local working directory
```


## Debug

See /tmp/lightblue-metadata-manager-debug.log. This is where all debug statements are logged, including lightblue-client logs.

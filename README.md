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

### Define metadata change as a patch

Convenient for applying the same change to multiple environments where metadata state is unknown (other changes may be present).

#### RFC 6902 JSON patch

JSON patch works well for all cases which do not involve making changes to array elements. The problem with array elements is that JSON patch diff
is refers to array indexes and will produce undesired results when elements in an array were shifted or reordered.

Prepare a patch:
```
lbmd pull --env dev -e user -v newest # saves newest user.json version in your current directory
vim user.json # make changes in user metadata
lbmd diff --env dev -e user > patch.json # diff your local user copy against newest user version in Lightblue, save the diff
```

Apply the patch in higher env:
```
lbmd pull --env qa -e user -v newest
lbmd apply -e user -jp patch.json # Apply the patch locally
lbmd diff --env qa -e user # diff your local user copy against newest user version in Lightblue, just to make sure
lbmd push --env qa -e user # Update metadata in Lightblue in qa
```

#### JavaScript patch

Considering JSON patch limitations, you can choose to describe your metadata changes in javascript.

Example javascript patch:
```javascript
// remove "personalInformation.firstName" projection from notificationsHook
entity.entityInfo.hooks.forEach(function(hook) {

 if (hook.name != "notificationHook") {
   return;
 }

 hook.configuration.includeProjection.remove(function(p) {
   return p.field=="personalInformation.firstName";
 });

});
```

The remove function was added to Array.prototype for convinience. See [util.js](src/main/resources/util.js) for other embedded utilities.

The logic refers to array elements by unique field values, so it will produce desired results regardless of the order.

To apply javascript patch to your local copy:
```
lbmd apply -e user -jsp patch.js
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

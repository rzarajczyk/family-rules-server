# Group States Feature

## Overview

The Group States feature allows users to define custom states for app groups. Each group state represents a specific configuration where multiple devices can be set to different device states simultaneously.

## Example Use Case

For a group called "Tomek", you can define a state named "Locked" which specifies:
- Device "Tomek - mobile" should be set to device state "Locked restricted apps"
- Device "Tomek - desktop" should be set to device state "Logged out"

This allows you to quickly apply consistent states across multiple devices with a single action.

## User Interface

### Accessing Group States

1. Navigate to the Groups page
2. Expand an app group
3. Click the "Define Group States" button (next to "Select Apps")

### Managing Group States

The Group States modal provides the following functionality:

- **View States**: See all defined states for the group with their device configurations
- **Add New State**: Click "Add New State" to create a new group state
- **Edit State**: Click the edit icon on any state to modify it
- **Delete State**: Click the delete icon to remove a state

### Creating/Editing a Group State

1. Enter a name for the state (e.g., "Locked", "Study Time", "Free Time")
2. For each device, select the desired device state from the dropdown
3. Click "Save" to apply changes

## Backend API

### Endpoints

#### Get Group States
```
GET /bff/app-groups/{groupId}/states
```
Returns all states defined for a group.

#### Create Group State
```
POST /bff/app-groups/{groupId}/states
```
Request body:
```json
{
  "name": "Locked",
  "deviceStates": {
    "device-uuid-1": {
      "deviceState": "LOCKED",
      "extra": null
    },
    "device-uuid-2": {
      "deviceState": "LOGGED_OUT",
      "extra": null
    }
  }
}
```

#### Update Group State
```
PUT /bff/app-groups/{groupId}/states/{stateId}
```
Request body: Same as create

#### Delete Group State
```
DELETE /bff/app-groups/{groupId}/states/{stateId}
```

#### Get Devices for States Configuration
```
GET /bff/app-groups/{groupId}/devices-for-states
```
Returns all user's devices with their available device states.

## Database Schema

Group states are stored in Firestore under the following structure:
```
users/{userId}/appGroups/{groupId}/groupStates/{stateId}
```

Each group state document contains:
- `id`: Unique identifier
- `name`: Display name of the state
- `deviceStates`: JSON map of device IDs to device state objects

## Implementation Details

### Backend Components

- **GroupStateService**: Business logic for managing group states
- **GroupStateRepository**: Interface for persistence operations
- **FirestoreGroupStateRepository**: Firestore implementation
- **BffGroupStatesController**: REST API endpoints

### Frontend Components

- **groups.html**: Updated with new modal UIs
- **groups.js**: JavaScript logic for state management
- **app-group-collapsible.handlebars**: Template updated with "Define Group States" button

## Future Enhancements

Potential improvements for this feature:

1. **Quick Apply**: Add ability to quickly apply a group state to all devices
2. **State Templates**: Pre-defined state templates for common scenarios
3. **State Scheduling**: Schedule automatic state changes at specific times
4. **State Inheritance**: Allow states to inherit from other states
5. **Bulk Operations**: Apply the same state across multiple groups

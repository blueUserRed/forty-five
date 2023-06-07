
# How to...

### add a new card

- Add an entry into the cards.onj file for the card
- Add the texture for the card into the textures/cards directory
- Update the card_generator_config.onj file, so it generates a texture
  for the card
- For the next start of the game, set the `generateCards` boolean in
  the `FortyFive` object to `true`
- TODO: how the player can get card

### add a new Actor

- Create a class for your actor by either extending an existing one
  (like CustomImage, CustomLabel) or by extending Widget
- Update the `getWidget` function of the ScreenBuilder to create the
  new Widget
- Update the screen.onjschema file by adding the new Actor

### make an Actor styleable

- In order for an actor to be compatible with the style-system, it must
  implement the StyledActor interface. Override the `styleManager` property
  and initialise it to `null`. This property will be set automatically by the
  ScreenBuilder.
- Override the `initStyles` function, in which the style-properties of the
  actor are added to the StyleManager. The easiest way to do this is using
  the extension functions defined in StyleProperties.kt. The
  `addActorStyles` extension function can be used by any actor and adds the
  fundamental styles all actors have. There are also extension functions for
  specific actors, for example for actors that have backgrounds.
- The StyledActor interface also forces you to implement the HoverStateActor
  interface in order for the hover style-condition to work.

### make an actor selectable with the keyboard

- Implement the KeySelectableActor interface.
- Override the `isSelected` property and initialise it to `false`.
- Override the `partOfHierarchyProperty`. Typically, this property is padded 
  through the constructor and read by the ScreenBuilder from the screen file.
- Override the `getHighlightArea` function. This function returns a rectangle
  with the coordinates, width and height of the actor. Don't forget to
  convert the local coordinates to the screen coordinates, for example by using
  `localToStageCoordinates(Vector2(0f, 0f))`.

TODO: more items

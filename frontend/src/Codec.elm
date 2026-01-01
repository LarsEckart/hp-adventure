module Codec exposing (decodeGameState, encodeGameState)

import Json.Decode as Decode exposing (Decoder)
import Json.Encode as Encode
import Model

encodeGameState : Model.GameState -> Encode.Value
encodeGameState state =
    Encode.object
        [ ( "schemaVersion", Encode.int state.schemaVersion )
        , ( "player", encodePlayer state.player )
        , ( "currentAdventure", encodeMaybe encodeAdventure state.currentAdventure )
        ]


decodeGameState : Decoder Model.GameState
decodeGameState =
    Decode.oneOf
        [ decodeCurrent
        , decodeLegacy
        , Decode.null Model.defaultState
        ]


decodeCurrent : Decoder Model.GameState
decodeCurrent =
    Decode.map3
        (\_ player currentAdventure ->
            { schemaVersion = 2
            , player = player
            , currentAdventure = currentAdventure
            , actionInput = ""
            , isLoading = False
            , error = Nothing
            , notice = Nothing
            , showInventory = True
            , showHistory = True
            , pendingAbandon = False
            }
        )
        (Decode.field "schemaVersion" Decode.int)
        (Decode.field "player" decodePlayer)
        (Decode.maybe (Decode.field "currentAdventure" decodeAdventure))


decodeLegacy : Decoder Model.GameState
decodeLegacy =
    Decode.map2
        (\playerName houseName ->
            { schemaVersion = 2
            , player =
                { name = playerName
                , houseName = houseName
                , inventory = []
                , completedAdventures = []
                , stats = { adventuresCompleted = 0, totalTurns = 0 }
                }
            , currentAdventure = Nothing
            , actionInput = ""
            , isLoading = False
            , error = Nothing
            , notice = Nothing
            , showInventory = True
            , showHistory = True
            , pendingAbandon = False
            }
        )
        (Decode.field "playerName" Decode.string)
        (Decode.field "houseName" Decode.string)


encodePlayer : Model.Player -> Encode.Value
encodePlayer player =
    Encode.object
        [ ( "name", Encode.string player.name )
        , ( "houseName", Encode.string player.houseName )
        , ( "inventory", Encode.list encodeItem player.inventory )
        , ( "completedAdventures", Encode.list encodeCompletedAdventure player.completedAdventures )
        , ( "stats", encodeStats player.stats )
        ]


decodePlayer : Decoder Model.Player
decodePlayer =
    Decode.map5 Model.Player
        (Decode.field "name" Decode.string)
        (Decode.field "houseName" Decode.string)
        (Decode.oneOf
            [ Decode.field "inventory" (Decode.list decodeItem)
            , Decode.succeed []
            ]
        )
        (Decode.oneOf
            [ Decode.field "completedAdventures" (Decode.list decodeCompletedAdventure)
            , Decode.succeed []
            ]
        )
        (Decode.oneOf
            [ Decode.field "stats" decodeStats
            , Decode.succeed { adventuresCompleted = 0, totalTurns = 0 }
            ]
        )


encodeAdventure : Model.Adventure -> Encode.Value
encodeAdventure adventure =
    Encode.object
        [ ( "title", encodeMaybe Encode.string adventure.title )
        , ( "startedAt", Encode.string adventure.startedAt )
        , ( "turns", Encode.list encodeTurn adventure.turns )
        ]


decodeAdventure : Decoder Model.Adventure
decodeAdventure =
    Decode.map3 Model.Adventure
        (Decode.maybe (Decode.field "title" Decode.string))
        (Decode.field "startedAt" Decode.string)
        (Decode.oneOf
            [ Decode.field "turns" (Decode.list decodeTurn)
            , Decode.succeed []
            ]
        )


encodeTurn : Model.Turn -> Encode.Value
encodeTurn turn =
    Encode.object
        [ ( "userAction", Encode.string turn.userAction )
        , ( "assistant", encodeMaybe encodeAssistant turn.assistant )
        ]


decodeTurn : Decoder Model.Turn
decodeTurn =
    Decode.map2 Model.Turn
        (Decode.field "userAction" Decode.string)
        (Decode.maybe (Decode.field "assistant" decodeAssistant))


encodeAssistant : Model.AssistantTurn -> Encode.Value
encodeAssistant assistant =
    Encode.object
        [ ( "storyText", Encode.string assistant.storyText )
        , ( "suggestedActions", Encode.list Encode.string assistant.suggestedActions )
        , ( "newItems", Encode.list encodeItem assistant.newItems )
        , ( "adventureCompleted", Encode.bool assistant.adventureCompleted )
        , ( "image", encodeMaybe encodeImage assistant.image )
        ]


decodeAssistant : Decoder Model.AssistantTurn
decodeAssistant =
    Decode.map5 Model.AssistantTurn
        (Decode.field "storyText" Decode.string)
        (Decode.oneOf
            [ Decode.field "suggestedActions" (Decode.list Decode.string)
            , Decode.succeed []
            ]
        )
        (Decode.oneOf
            [ Decode.field "newItems" (Decode.list decodeItem)
            , Decode.succeed []
            ]
        )
        (Decode.oneOf
            [ Decode.field "adventureCompleted" Decode.bool
            , Decode.succeed False
            ]
        )
        (Decode.oneOf
            [ Decode.field "image" (Decode.maybe decodeImage)
            , Decode.succeed Nothing
            ]
        )


encodeMaybe : (a -> Encode.Value) -> Maybe a -> Encode.Value
encodeMaybe encoder maybeValue =
    case maybeValue of
        Nothing ->
            Encode.null

        Just value ->
            encoder value


encodeItem : Model.Item -> Encode.Value
encodeItem item =
    Encode.object
        [ ( "name", Encode.string item.name )
        , ( "description", Encode.string item.description )
        , ( "foundAt", Encode.string item.foundAt )
        ]


decodeItem : Decoder Model.Item
decodeItem =
    Decode.map3 Model.Item
        (Decode.field "name" Decode.string)
        (Decode.field "description" Decode.string)
        (Decode.field "foundAt" Decode.string)


encodeImage : Model.ImageData -> Encode.Value
encodeImage image =
    Encode.object
        [ ( "mimeType", Encode.string image.mimeType )
        , ( "base64", Encode.string image.base64 )
        , ( "prompt", encodeMaybe Encode.string image.prompt )
        ]


decodeImage : Decoder Model.ImageData
decodeImage =
    Decode.map3 Model.ImageData
        (Decode.field "mimeType" Decode.string)
        (Decode.field "base64" Decode.string)
        (Decode.maybe (Decode.field "prompt" Decode.string))


encodeCompletedAdventure : Model.CompletedAdventure -> Encode.Value
encodeCompletedAdventure adventure =
    Encode.object
        [ ( "title", Encode.string adventure.title )
        , ( "summary", Encode.string adventure.summary )
        , ( "completedAt", Encode.string adventure.completedAt )
        ]


decodeCompletedAdventure : Decoder Model.CompletedAdventure
decodeCompletedAdventure =
    Decode.map3 Model.CompletedAdventure
        (Decode.field "title" Decode.string)
        (Decode.field "summary" Decode.string)
        (Decode.field "completedAt" Decode.string)


encodeStats : Model.Stats -> Encode.Value
encodeStats stats =
    Encode.object
        [ ( "adventuresCompleted", Encode.int stats.adventuresCompleted )
        , ( "totalTurns", Encode.int stats.totalTurns )
        ]


decodeStats : Decoder Model.Stats
decodeStats =
    Decode.map2 Model.Stats
        (Decode.oneOf
            [ Decode.field "adventuresCompleted" Decode.int
            , Decode.succeed 0
            ]
        )
        (Decode.oneOf
            [ Decode.field "totalTurns" Decode.int
            , Decode.succeed 0
            ]
        )

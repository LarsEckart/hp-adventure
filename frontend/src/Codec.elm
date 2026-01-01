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
        (\schemaVersion player currentAdventure ->
            { schemaVersion = schemaVersion
            , player = player
            , currentAdventure = currentAdventure
            , actionInput = ""
            , isLoading = False
            , error = Nothing
            }
        )
        (Decode.field "schemaVersion" Decode.int)
        (Decode.field "player" decodePlayer)
        (Decode.maybe (Decode.field "currentAdventure" decodeAdventure))


decodeLegacy : Decoder Model.GameState
decodeLegacy =
    Decode.map2
        (\playerName houseName ->
            { schemaVersion = 1
            , player = { name = playerName, houseName = houseName }
            , currentAdventure = Nothing
            , actionInput = ""
            , isLoading = False
            , error = Nothing
            }
        )
        (Decode.field "playerName" Decode.string)
        (Decode.field "houseName" Decode.string)


encodePlayer : Model.Player -> Encode.Value
encodePlayer player =
    Encode.object
        [ ( "name", Encode.string player.name )
        , ( "houseName", Encode.string player.houseName )
        ]


decodePlayer : Decoder Model.Player
decodePlayer =
    Decode.map2 Model.Player
        (Decode.field "name" Decode.string)
        (Decode.field "houseName" Decode.string)


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
        ]


decodeAssistant : Decoder Model.AssistantTurn
decodeAssistant =
    Decode.map2 Model.AssistantTurn
        (Decode.field "storyText" Decode.string)
        (Decode.oneOf
            [ Decode.field "suggestedActions" (Decode.list Decode.string)
            , Decode.succeed []
            ]
        )


encodeMaybe : (a -> Encode.Value) -> Maybe a -> Encode.Value
encodeMaybe encoder maybeValue =
    case maybeValue of
        Nothing ->
            Encode.null

        Just value ->
            encoder value

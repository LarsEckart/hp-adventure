module Codec exposing (decodeGameState, encodeGameState)

import Json.Decode as Decode exposing (Decoder)
import Json.Encode as Encode
import Model

encodeGameState : Model.GameState -> Encode.Value
encodeGameState state =
    Encode.object
        [ ( "schemaVersion", Encode.int state.schemaVersion )
        , ( "playerName", Encode.string state.playerName )
        , ( "houseName", Encode.string state.houseName )
        , ( "ready", Encode.bool state.ready )
        ]


decodeGameState : Decoder Model.GameState
decodeGameState =
    Decode.oneOf
        [ Decode.map4 Model.GameState
            (Decode.field "schemaVersion" Decode.int)
            (Decode.field "playerName" Decode.string)
            (Decode.field "houseName" Decode.string)
            (Decode.field "ready" Decode.bool)
        , Decode.null Model.defaultState
        ]

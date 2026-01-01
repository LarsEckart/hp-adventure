port module Main exposing (main)

import Browser
import Codec
import Json.Decode as Decode
import Json.Encode as Encode
import Model
import Msg exposing (Msg)
import Update
import View

port saveState : Encode.Value -> Cmd msg

main : Program Decode.Value Model.GameState Msg
main =
    Browser.element
        { init = init
        , update = Update.update save
        , view = View.view
        , subscriptions = always Sub.none
        }

init : Decode.Value -> ( Model.GameState, Cmd Msg )
init flags =
    case Decode.decodeValue Codec.decodeGameState flags of
        Ok state ->
            ( state, Cmd.none )

        Err _ ->
            ( Model.defaultState, Cmd.none )

save : Model.GameState -> Cmd Msg
save state =
    saveState (Codec.encodeGameState state)

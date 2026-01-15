port module Main exposing (main)

import Browser
import Codec
import Http
import Json.Decode as Decode
import Json.Encode as Encode
import Model
import Msg exposing (Msg)
import Update
import View

port saveState : Encode.Value -> Cmd msg
port clearState : () -> Cmd msg
port savePassword : String -> Cmd msg
port onlineStatus : (Bool -> msg) -> Sub msg
port startStoryStream : Encode.Value -> Cmd msg
port storyStream : (Decode.Value -> msg) -> Sub msg
port speakStory : String -> Cmd msg
port ttsError : (String -> msg) -> Sub msg

main : Program Decode.Value Model.GameState Msg
main =
    Browser.element
        { init = init
        , update = Update.update save startStoryStream speakStory (clearState ()) validatePassword
        , view = View.view
        , subscriptions = always (Sub.batch [ onlineStatus Msg.OnlineStatusChanged, storyStream Msg.GotStoryStreamEvent, ttsError Msg.GotTtsError ])
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
    Cmd.batch
        [ saveState (Codec.encodeGameState state)
        , savePassword state.passwordInput
        ]


validatePassword : String -> Cmd Msg
validatePassword password =
    Http.request
        { method = "POST"
        , headers = [ Http.header "X-App-Password" password ]
        , url = "/api/auth/validate"
        , body = Http.emptyBody
        , expect = Http.expectWhatever Msg.GotAuthResponse
        , timeout = Just 10000
        , tracker = Nothing
        }

module Msg exposing (Msg(..))

import Browser.Dom as Dom
import Api
import Json.Decode as Decode
import Http
import Time

type Msg
    = UpdateName String
    | UpdateHouse String
    | UpdateActionInput String
    | UpdatePasswordInput String
    | OnlineStatusChanged Bool
    | SubmitPassword
    | GotAuthResponse (Result Http.Error ())
    | StartAdventure
    | GotStartTime Time.Posix
    | SendAction
    | UseSuggestedAction String
    | GotStoryResponse (Result Api.HttpError Api.StoryResponse)
    | GotStoryStreamEvent Decode.Value
    | GotTtsError String
    | ToggleHistory
    | RequestAbandon
    | ConfirmAbandon
    | CancelAbandon
    | FinishAdventure
    | DismissNotice
    | ResetState
    | ScrolledToBottom (Result Dom.Error ())

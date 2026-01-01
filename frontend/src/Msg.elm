module Msg exposing (Msg(..))

import Browser.Dom as Dom
import Api
import Json.Decode as Decode
import Time

type Msg
    = UpdateName String
    | UpdateHouse String
    | UpdateActionInput String
    | OnlineStatusChanged Bool
    | StartAdventure
    | GotStartTime Time.Posix
    | SendAction
    | UseSuggestedAction String
    | GotStoryResponse (Result Api.HttpError Api.StoryResponse)
    | GotStoryStreamEvent Decode.Value
    | ToggleInventory
    | ToggleHistory
    | RequestAbandon
    | ConfirmAbandon
    | CancelAbandon
    | FinishAdventure
    | DismissNotice
    | ResetState
    | ScrolledToBottom (Result Dom.Error ())

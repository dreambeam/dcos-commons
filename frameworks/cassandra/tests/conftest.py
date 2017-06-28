import os
import json
import pytest
import shakedown


@pytest.fixture(scope='session', autouse=True)
def configure_universe(request):
    stub_urls = {}

    # prepare needed universe repositories
    stub_universe_urls = os.environ.get('STUB_UNIVERSE_URL')
    if not stub_universe_urls:
        return
    for url in stub_universe_urls.split(' '):
        package_name = url.split('/')[4] # TODO: does this need to be a random string?
        stub_urls[package_name] = url

    # clear out the added universe repositores at testing end
    def unconfigure_universe():
        for name, url in stub_urls.items():
            remove_package_cmd = 'dcos package repo remove {}'.format(name)
            shakedown.run_dcos_command(remove_package_cmd)
    request.addfinalizer(unconfigure_universe)

    # clean up any duplicate repositories
    # TODO: is this needed, or do duplicate repositories signify an actual error?
    current_universes = shakedown.run_dcos_command('dcos package repo list --json')
    for repo in json.loads(current_universes)['repositories']:
        if repo['uri'] in stub_urls.values():
            remove_package_cmd = 'dcos package repo remove {}'.format(repo['name'])
            shakedown.run_dcos_command(remove_package_cmd)

    # add the needed universe repositories
    for name, url in stub_urls.items():
        add_package_cmd = 'dcos package repo add --index=0 {} {}'.format(name, url)
        shakedown.run_dcos_command(add_package_cmd)
